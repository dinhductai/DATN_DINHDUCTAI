package com.microsv.task_service.controller.internal;

import com.microsv.task_service.dto.response.TaskResponse;
import com.microsv.task_service.enumeration.PriorityLevel;
import com.microsv.task_service.enumeration.TaskStatus;
import com.microsv.task_service.service.TaskService;
import com.microsv.task_service.service.TaskCacheService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/internal/tasks")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaskInternalController {
    TaskService taskService;
    TaskCacheService taskCacheService;

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasks(@RequestHeader("userId") Long userId) {
        List<TaskResponse> responses = taskService.getAllTasksByUser(userId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/ai/json")
    public ResponseEntity<String> getTasksJsonForAI(@RequestHeader("userId") Long userId) {
        // Try to get from cache and refresh TTL if exists
        String cachedJson = taskCacheService.getCachedTaskJsonWithRefresh(userId);
        if (cachedJson != null) {
            return ResponseEntity.ok(cachedJson);
        }

        // Cache miss - load from DB, convert with Claude, cache, then return
        taskService.syncTasksToCache(userId);
        String newJson = taskCacheService.getCachedTaskJson(userId);
        if (newJson != null) {
            return ResponseEntity.ok(newJson);
        }

        return ResponseEntity.ok("{\"tasks\": []}");
    }

    @PostMapping("/ai/refresh")
    public ResponseEntity<Void> refreshTaskCache(@RequestHeader("userId") Long userId) {
        taskService.syncTasksToCache(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/filter")
    public ResponseEntity<List<TaskResponse>> getFilteredTasks(
            @RequestHeader("userId") Long userId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) PriorityLevel priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "20") Integer limit
    ) {
        List<TaskResponse> responses = taskService.getFilteredTasks(userId, status, priority, fromDate, toDate, limit);
        return ResponseEntity.ok(responses);
    }

}
