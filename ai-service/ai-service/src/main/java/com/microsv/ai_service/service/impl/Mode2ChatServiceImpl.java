package com.microsv.ai_service.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsv.ai_service.client.TaskClient;
import com.microsv.ai_service.dto.response.Mode2ChatResponse;
import com.microsv.ai_service.service.Mode2ChatService;
import com.microsv.ai_service.service.Mode2PromptService;
import com.microsv.ai_service.util.TimeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class Mode2ChatServiceImpl implements Mode2ChatService {

    public static final String CONVERSATION_ID_PREFIX = "2_";

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
            String taskJson = getTaskJsonFromRedis(userId);
            TimeContext timeCtx = new TimeContext();
            String systemPrompt = mode2PromptService.buildSystemPrompt(taskJson, timeCtx);

            String rawResponse = chatClient.prompt()
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .system(systemPrompt)
                    .user(message)
                    .call()
                    .content()
                    .trim();

            Mode2ChatResponse response = parseToMode2Response(rawResponse);
            response.setStructured(true);
            response.setCanApply(false); // Mode 2 chỉ tư vấn, không tự động apply
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

    private String getTaskJsonFromRedis(Long userId) {
        try {
            String json = taskClient.getTasksJsonForAI(userId);
            return json != null && !json.isEmpty() ? json : "{\"summary\":{},\"tasks\":[]}";
        } catch (Exception e) {
            log.warn("Failed to get task JSON from Redis for user {}: {}", userId, e.getMessage());
            return "{\"summary\":{\"totalTasks\":0,\"eventCount\":0},\"tasks\":[]}";
        }
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

            return Mode2ChatResponse.builder()
                    .structured(root.has("structured") ? root.get("structured").asBoolean() : true)
                    .message(safeText(root, "message"))
                    .canApply(false)
                    .schedule(schedule)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse Mode2 JSON response, returning as plain text: {}", e.getMessage());
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
