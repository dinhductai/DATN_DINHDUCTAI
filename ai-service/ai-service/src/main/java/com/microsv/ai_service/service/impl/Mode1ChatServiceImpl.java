package com.microsv.ai_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsv.ai_service.client.TaskClient;
import com.microsv.ai_service.dto.response.Mode1ChatResponse;
import com.microsv.ai_service.service.Mode1ChatService;
import com.microsv.ai_service.service.Mode1PromptService;
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
public class Mode1ChatServiceImpl implements Mode1ChatService {

    public static final String CONVERSATION_ID_PREFIX = "1_";

    private final ChatClient chatClient;
    private final TaskClient taskClient;
    private final Mode1PromptService mode1PromptService;
    private final ObjectMapper objectMapper;

    public Mode1ChatServiceImpl(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            TaskClient taskClient,
            Mode1PromptService mode1PromptService,
            ObjectMapper objectMapper) {
        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.taskClient = taskClient;
        this.mode1PromptService = mode1PromptService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mode1ChatResponse chat(String message, String conversationId, Long userId) {
        if (message == null || message.trim().isEmpty()) {
            message = "Cho tôi xem tổng quan lịch trình hôm nay";
        }

        try {
            String taskJson = getTaskJsonFromRedis(userId);
            TimeContext timeCtx = new TimeContext();
            String systemPrompt = mode1PromptService.buildSystemPrompt(taskJson, timeCtx);

            String rawResponse = chatClient.prompt()
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .system(systemPrompt)
                    .user(message)
                    .call()
                    .content()
                    .trim();

            return parseToMode1Response(rawResponse);

        } catch (Exception e) {
            log.error("Error in Mode1 chat for user {}: {}", userId, e.getMessage(), e);
            return Mode1ChatResponse.builder()
                    .structured(false)
                    .message("Xin lỗi, đã có lỗi xảy ra. Vui lòng thử lại.")
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

    private Mode1ChatResponse parseToMode1Response(String rawJson) {
        String cleaned = rawJson.trim();
        if (!cleaned.startsWith("{")) {
            int firstBrace = cleaned.indexOf('{');
            if (firstBrace >= 0) {
                cleaned = cleaned.substring(firstBrace);
            }
        }

        try {
            JsonNode root = objectMapper.readTree(cleaned);

            Mode1ChatResponse.Summary summary = null;
            if (root.has("summary") && !root.get("summary").isNull()) {
                JsonNode s = root.get("summary");
                summary = Mode1ChatResponse.Summary.builder()
                        .totalTasks(safeInt(s, "totalTasks"))
                        .todoCount(safeInt(s, "todoCount"))
                        .inProgressCount(safeInt(s, "inProgressCount"))
                        .doneCount(safeInt(s, "doneCount"))
                        .overdueCount(safeInt(s, "overdueCount"))
                        .eventCount(safeInt(s, "eventCount"))
                        .build();
            }

            List<Mode1ChatResponse.TaskItem> taskItems = new ArrayList<>();
            if (root.has("tasks") && root.get("tasks").isArray()) {
                for (JsonNode t : root.get("tasks")) {
                    taskItems.add(Mode1ChatResponse.TaskItem.builder()
                            .taskId(safeLong(t, "taskId"))
                            .title(safeText(t, "title"))
                            .deadline(safeText(t, "deadline"))
                            .deadlineInfo(safeText(t, "deadlineInfo"))
                            .priority(safeText(t, "priority"))
                            .status(safeText(t, "status"))
                            .isEvent(t.has("isEvent") && !t.get("isEvent").isNull() ? t.get("isEvent").asBoolean() : null)
                            .reason(safeText(t, "reason"))
                            .build());
                }
            }

            List<Mode1ChatResponse.EventItem> eventItems = new ArrayList<>();
            if (root.has("events") && root.get("events").isArray()) {
                for (JsonNode e : root.get("events")) {
                    eventItems.add(Mode1ChatResponse.EventItem.builder()
                            .taskId(safeLong(e, "taskId"))
                            .eventId(safeLong(e, "eventId"))
                            .title(safeText(e, "title"))
                            .startTime(safeText(e, "startTime"))
                            .location(safeText(e, "location"))
                            .isOnline(e.has("isOnline") && !e.get("isOnline").isNull() ? e.get("isOnline").asBoolean() : null)
                            .priority(safeText(e, "priority"))
                            .reason(safeText(e, "reason"))
                            .build());
                }
            }

            Mode1ChatResponse.Highlight highlight = null;
            if (root.has("highlight") && !root.get("highlight").isNull()) {
                JsonNode h = root.get("highlight");
                highlight = Mode1ChatResponse.Highlight.builder()
                        .mostUrgent(safeText(h, "mostUrgent"))
                        .mostImportant(safeText(h, "mostImportant"))
                        .build();
            }

            return Mode1ChatResponse.builder()
                    .structured(root.has("structured") ? root.get("structured").asBoolean() : true)
                    .message(safeText(root, "message"))
                    .answerType(safeText(root, "answerType"))
                    .summary(summary)
                    .tasks(taskItems)
                    .events(eventItems)
                    .highlight(highlight)
                    .build();

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Mode1 JSON response, returning as plain text: {}", e.getMessage());
            return Mode1ChatResponse.builder()
                    .structured(false)
                    .message(rawJson)
                    .build();
        }
    }

    private String safeText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private int safeInt(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : 0;
    }

    private Long safeLong(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asLong() : null;
    }
}
