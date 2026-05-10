package com.microsv.task_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsv.task_service.entity.Event;
import com.microsv.task_service.entity.Task;
import com.microsv.task_service.repository.EventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClaudeTaskConvertService {

    @Value("${claude.api.key}")
    private String claudeApiKey;

    @Value("${claude.api.ai-conversion-enabled:false}")
    private boolean aiConversionEnabled;

    private final ObjectMapper objectMapper;
    private final TaskCacheService taskCacheService;
    private final TaskJsonConvertService taskJsonConvertService;
    private final EventRepository eventRepository;

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";

    public ClaudeTaskConvertService(
            TaskCacheService taskCacheService,
            TaskJsonConvertService taskJsonConvertService,
            EventRepository eventRepository) {
        this.taskCacheService = taskCacheService;
        this.taskJsonConvertService = taskJsonConvertService;
        this.eventRepository = eventRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Convert tasks to AI-chatbot-friendly JSON.
     * Uses local Java conversion by default (free, fast).
     * Falls back to Claude AI only when claude.api.ai-conversion-enabled = true.
     * Automatically merges event data when available.
     */
    public String convertTasksToJson(Long userId, List<Task> tasks) {
        if (!aiConversionEnabled) {
            log.debug("AI conversion disabled — using local TaskJsonConvertService for user {}", userId);
            return taskJsonConvertService.convertTasksToJson(tasks, Map.of());
        }

        try {
            String taskData = objectMapper.writeValueAsString(tasks);

            String prompt = """
                Convert the following task data to a clean, AI-chatbot-friendly JSON format.

                Requirements:
                - Output ONLY valid JSON, no explanation text
                - Include all tasks with: title, description, deadline, status, priority, createdAt, completedAt
                - Deadlines should be in Vietnamese format (dd/MM/yyyy HH:mm)
                - Status should be: "Chưa làm" (TODO), "Đang làm" (IN_PROGRESS), "Hoàn thành" (DONE)
                - Priority should be: "Cao" (HIGH), "Trung bình" (MEDIUM), "Thấp" (LOW)
                - Add a summary section at the top with: totalTasks, todoCount, inProgressCount, doneCount, overdueCount
                - Add friendly deadline info: "Còn X ngày" or "Quá hạn X ngày"

                TASK DATA:
                %s
                """.formatted(taskData);

            Map<String, Object> response = callClaudeApi(prompt);
            return extractTextFromResponse(response);

        } catch (Exception e) {
            log.error("Failed to convert tasks to JSON for user {}: {}", userId, e.getMessage());
            return taskJsonConvertService.convertTasksToJson(tasks, Map.of());
        }
    }

    /**
     * Convert tasks with event data merged in.
     * Local-only (no AI fallback) — uses TaskJsonConvertService directly.
     */
    public String convertTasksToJsonWithEvents(Long userId, List<Task> tasks) {
        if (!aiConversionEnabled) {
            log.debug("Converting tasks with events using local service for user {}", userId);
            return convertTasksToJsonWithEventsLocal(tasks);
        }

        try {
            String taskData = objectMapper.writeValueAsString(tasks);

            String prompt = """
                Convert the following task + event data to a clean, AI-chatbot-friendly JSON format.

                Requirements:
                - Output ONLY valid JSON, no explanation text
                - Include all tasks with: taskId, title, description, deadline, status, priority, createdAt, completedAt
                - Deadlines should be in Vietnamese format (dd/MM/yyyy HH:mm)
                - Status should be: "Chưa làm" (TODO), "Đang làm" (IN_PROGRESS), "Hoàn thành" (DONE)
                - Priority should be: "Cao" (HIGH), "Trung bình" (MEDIUM), "Thấp" (LOW)
                - Add a summary section at the top with: totalTasks, todoCount, inProgressCount, doneCount, overdueCount, eventCount
                - Add friendly deadline info: "Còn X ngày" or "Quá hạn X ngày"

                TASK DATA:
                %s
                """.formatted(taskData);

            Map<String, Object> response = callClaudeApi(prompt);
            return extractTextFromResponse(response);

        } catch (Exception e) {
            log.error("Failed to convert tasks+events to JSON for user {}: {}", userId, e.getMessage());
            return convertTasksToJsonWithEventsLocal(tasks);
        }
    }

    /**
     * Local conversion with event data merged — no AI call.
     */
    public String convertTasksToJsonWithEvents(List<Task> tasks) {
        return convertTasksToJsonWithEventsLocal(tasks);
    }

    private String convertTasksToJsonWithEventsLocal(List<Task> tasks) {
        Map<Long, Event> taskIdToEvent = buildTaskIdToEventMap(tasks);
        return taskJsonConvertService.convertTasksToJson(tasks, taskIdToEvent);
    }

    private Map<Long, Event> buildTaskIdToEventMap(List<Task> tasks) {
        List<Long> taskIds = tasks.stream()
                .map(Task::getTaskId)
                .filter(Objects::nonNull)
                .toList();

        if (taskIds.isEmpty()) {
            return Map.of();
        }

        List<Event> events = eventRepository.findByTaskIdIn(taskIds);
        return events.stream()
                .collect(Collectors.toMap(Event::getTaskId, e -> e, (a, b) -> a));
    }

    public void syncUserTasks(Long userId, List<Task> tasks) {
        taskCacheService.cacheUserTasks(userId, tasks);
        String aiJson = convertTasksToJsonWithEvents(userId, tasks);
        taskCacheService.cacheTaskJson(userId, aiJson);
        log.info("Synced {} tasks (with events) for user {} to Redis", tasks.size(), userId);
    }

    private Map<String, Object> callClaudeApi(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "claude-sonnet-4-20250514");
        requestBody.put("max_tokens", 4096);
        requestBody.put("temperature", 0.3);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        requestBody.put("messages", List.of(message));

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("x-api-key", claudeApiKey);
        headers.set("anthropic-version", "2023-06-01");

        org.springframework.http.HttpEntity<Map<String, Object>> entity =
                new org.springframework.http.HttpEntity<>(requestBody, headers);

        log.info("Calling Claude API with key: {}", claudeApiKey.substring(0, 10) + "...");

        org.springframework.http.ResponseEntity<Map> response = new org.springframework.web.client.RestTemplate().exchange(
                CLAUDE_API_URL,
                org.springframework.http.HttpMethod.POST,
                entity,
                Map.class
        );

        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromResponse(Map<String, Object> response) {
        if (response == null) return "{}";
        try {
            if (response.containsKey("content")) {
                List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
                if (content != null && !content.isEmpty()) {
                    return content.get(0).get("text").toString();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Claude response: {}", e.getMessage());
        }
        return "{}";
    }
}
