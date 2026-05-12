package com.microsv.ai_service.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsv.ai_service.client.TaskClient;
import com.microsv.ai_service.dto.response.Mode2ChatResponse;
import com.microsv.ai_service.service.Mode2ChatService;
import com.microsv.ai_service.service.Mode2PromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class Mode2ChatServiceImpl implements Mode2ChatService {

    public static final String CONVERSATION_ID_PREFIX = "2_";
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ChatClient chatClient;
    private final TaskClient taskClient;
    private final Mode2PromptService mode2PromptService;
    private final ObjectMapper objectMapper;

    public Mode2ChatServiceImpl(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            TaskClient taskClient,
            Mode2PromptService mode2PromptService,
            ObjectMapper objectMapper) {
        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.taskClient = taskClient;
        this.mode2PromptService = mode2PromptService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mode2ChatResponse chat(String message, String conversationId, Long userId) {
        if (message == null || message.trim().isEmpty()) {
            message = "Hãy phân tích và tư vấn sắp xếp lịch trình của tôi";
        }

        try {
            // Extract target date from user message
            LocalDate targetDate = extractDateFromMessage(message);

            String taskJson = getTaskJsonFromRedis(userId, targetDate);
            String systemPrompt = mode2PromptService.buildSystemPrompt(taskJson, targetDate);

            String rawResponse = chatClient.prompt()
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .system(systemPrompt)
                    .user(message)
                    .call()
                    .content()
                    .trim();

            Mode2ChatResponse response = parseToMode2Response(rawResponse);
            response.setStructured(true);
            response.setCanApply(false);
            return response;

        } catch (Exception e) {
            log.error("Error in Mode2 advisory for user {}: {}", userId, e.getMessage(), e);
            return Mode2ChatResponse.builder()
                    .structured(false)
                    .message("Xin lỗi, đã có lỗi xảy ra. Vui lòng thử lại.")
                    .canApply(false)
                    .build();
        }
    }

    private String getTaskJsonFromRedis(Long userId, LocalDate targetDate) {
        try {
            String json = taskClient.getTasksJsonForAI(userId);
            if (json == null || json.isEmpty() || json.equals("{}")) {
                return "{\"summary\":{},\"tasks\":[]}";
            }
            return filterTasksByDate(json, targetDate);
        } catch (Exception e) {
            log.warn("Failed to get task JSON from Redis for user {}: {}", userId, e.getMessage());
            return "{\"summary\":{},\"tasks\":[]}";
        }
    }

    /**
     * Filter tasks by target date, only include NOT DONE tasks.
     * deadlineRaw/createdAtRaw are UTC strings → convert to Vietnam time → check LocalDate.
     */
    private String filterTasksByDate(String json, LocalDate targetDate) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode tasksNode = root.get("tasks");
            if (tasksNode == null || !tasksNode.isArray()) {
                return json;
            }

            List<JsonNode> filtered = new ArrayList<>();
            for (JsonNode task : tasksNode) {
                JsonNode statusNode = task.get("statusCode");
                if (statusNode != null && "DONE".equals(statusNode.asText())) {
                    continue;
                }
                if (isOnDate(task, targetDate)) {
                    filtered.add(task);
                }
            }

            JsonNode newRoot = objectMapper.createObjectNode();
            ((com.fasterxml.jackson.databind.node.ObjectNode) newRoot).putPOJO("summary", null);
            ((com.fasterxml.jackson.databind.node.ObjectNode) newRoot).putPOJO("tasks", filtered);
            return objectMapper.writeValueAsString(newRoot);
        } catch (Exception e) {
            log.warn("Failed to filter tasks by date: {}", e.getMessage());
            return json;
        }
    }

    private boolean isOnDate(JsonNode task, LocalDate targetDate) {
        if (isOnDateUtc(task, "deadlineRaw", targetDate)) return true;
        if (isOnDateUtc(task, "createdAtRaw", targetDate)) return true;
        return isOnDateVietnam(task, "deadline", targetDate);
    }

    private boolean isOnDateUtc(JsonNode task, String field, LocalDate targetDate) {
        JsonNode node = task.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) return false;
        try {
            java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(node.asText());
            java.time.LocalDate d = odt.atZoneSameInstant(VIETNAM).toLocalDate();
            return d.equals(targetDate);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean isOnDateVietnam(JsonNode task, String field, LocalDate targetDate) {
        JsonNode node = task.get(field);
        if (node == null || node.isNull()) return false;
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(
                    node.asText().substring(0, 10),
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            return d.equals(targetDate);
        } catch (Exception e) {
            return false;
        }
    }

    private LocalDate extractDateFromMessage(String message) {
        // Try "ngày DD tháng MM" pattern
        Pattern p = Pattern.compile("ngày\\s*(\\d{1,2})\\s*tháng\\s*(\\d{1,2})", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(message);
        if (m.find()) {
            try {
                int day = Integer.parseInt(m.group(1));
                int month = Integer.parseInt(m.group(2));
                return LocalDate.of(LocalDate.now().getYear(), month, day);
            } catch (Exception e) {
                log.warn("Failed to parse date from: {}", message);
            }
        }

        // Try "DD/MM" or "DD-MM" pattern
        Pattern p2 = Pattern.compile("(\\d{1,2})[/\\-](\\d{1,2})", Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(message);
        if (m2.find()) {
            try {
                int day = Integer.parseInt(m2.group(1));
                int month = Integer.parseInt(m2.group(2));
                return LocalDate.of(LocalDate.now().getYear(), month, day);
            } catch (Exception e) {
                // ignore
            }
        }

        return LocalDate.now(VIETNAM);
    }

    private Mode2ChatResponse parseToMode2Response(String rawJson) {
        String cleaned = rawJson.trim();
        if (!cleaned.startsWith("{")) {
            int firstBrace = cleaned.indexOf('{');
            if (firstBrace >= 0) {
                cleaned = cleaned.substring(firstBrace);
            }
        }

        try {
            JsonNode root = objectMapper.readTree(cleaned);

            List<Mode2ChatResponse.ScheduledItem> schedule = new ArrayList<>();
            if (root.has("schedule") && root.get("schedule").isArray()) {
                for (JsonNode s : root.get("schedule")) {
                    schedule.add(Mode2ChatResponse.ScheduledItem.builder()
                            .taskId(safeLong(s, "taskId"))
                            .eventId(safeLong(s, "eventId"))
                            .type(safeText(s, "type"))
                            .title(safeText(s, "title"))
                            .startTime(safeText(s, "startTime"))
                            .deadline(safeText(s, "deadline"))
                            .category(safeText(s, "category"))
                            .build());
                }
            }

            List<String> advice = new ArrayList<>();
            if (root.has("advice") && root.get("advice").isArray()) {
                for (JsonNode a : root.get("advice")) {
                    advice.add(a.asText());
                }
            }

            return Mode2ChatResponse.builder()
                    .structured(root.has("structured") ? root.get("structured").asBoolean() : true)
                    .message(safeText(root, "message"))
                    .canApply(false)
                    .schedule(schedule)
                    .advice(advice)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse Mode2 JSON response: {}", e.getMessage());
            return Mode2ChatResponse.builder()
                    .structured(false)
                    .message(rawJson)
                    .canApply(false)
                    .build();
        }
    }

    private String safeText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private Long safeLong(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asLong() : null;
    }
}
