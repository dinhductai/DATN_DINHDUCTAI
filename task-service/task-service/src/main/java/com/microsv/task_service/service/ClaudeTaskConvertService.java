package com.microsv.task_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsv.task_service.entity.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ClaudeTaskConvertService {

    @Value("${claude.api.key}")
    private String claudeApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TaskCacheService taskCacheService;

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";

    public ClaudeTaskConvertService(TaskCacheService taskCacheService) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.taskCacheService = taskCacheService;
    }

    public String convertTasksToJson(Long userId, List<Task> tasks) {
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
            return generateFallbackJson(tasks);
        }
    }

    public void syncUserTasks(Long userId, List<Task> tasks) {
        taskCacheService.cacheUserTasks(userId, tasks);
        String aiJson = convertTasksToJson(userId, tasks);
        taskCacheService.cacheTaskJson(userId, aiJson);
        log.info("Synced {} tasks for user {} to Redis", tasks.size(), userId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callClaudeApi(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "claude-sonnet-4-20250514");
        requestBody.put("max_tokens", 4096);
        requestBody.put("temperature", 0.3);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        requestBody.put("messages", List.of(message));

        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", claudeApiKey);
        headers.put("anthropic-version", "2023-06-01");
        headers.put("Content-Type", "application/json");

        return restTemplate.postForObject(CLAUDE_API_URL, requestBody, Map.class, headers);
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

    private String generateFallbackJson(List<Task> tasks) {
        try {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("totalTasks", tasks.size());
            fallback.put("tasks", tasks);
            return objectMapper.writeValueAsString(fallback);
        } catch (Exception e) {
            return "{\"error\": \"Failed to convert tasks\"}";
        }
    }
}
