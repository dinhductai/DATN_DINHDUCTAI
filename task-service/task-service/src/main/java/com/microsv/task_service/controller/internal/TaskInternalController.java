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

    /**
     * Lấy task JSON cho AI.
     * LUÔN re-sync từ DB để đảm bảo AI nhìn thấy data mới nhất.
     * Không dùng cache stale — AI cần data chính xác.
     */
    @GetMapping("/ai/json")
    public ResponseEntity<String> getTasksJsonForAI(@RequestHeader("userId") Long userId) {
        // Luôn re-sync từ DB → đảm bảo AI luôn nhận data mới nhất
        taskService.syncTasksToCache(userId);
        String json = taskCacheService.getCachedTaskJson(userId);
        if (json != null) {
            return ResponseEntity.ok(json);
        }
        return ResponseEntity.ok("{\"summary\":{\"totalTasks\":0,\"eventCount\":0},\"tasks\":[]}");
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
