package com.microsv.ai_service.client;

import com.microsv.ai_service.dto.response.TaskResponse;
import com.microsv.ai_service.dto.request.TaskCreationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@FeignClient(name = "task-service")
public interface TaskClient {

    //lấy tất cả task thuộc user
    @GetMapping(value = "/internal/tasks")
    List<TaskResponse> getUserTasks(@RequestHeader("userId") Long userId);

    //lấy task đã lọc - phục vụ cho AI context thông minh
    @GetMapping(value = "/internal/tasks/filter")
    List<TaskResponse> getFilteredTasks(
            @RequestHeader("userId") Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "20") Integer limit
    );

    //lấy task JSON đã được convert từ Redis (task + event merged)
    @GetMapping(value = "/internal/tasks/ai/json")
    String getTasksJsonForAI(@RequestHeader("userId") Long userId);

    //cập nhật task từ AI (chỉ startTime, deadline)
    @PutMapping(value = "/internal/tasks/{taskId}/ai-update")
    TaskResponse updateTaskByAI(
            @PathVariable Long taskId,
            @RequestHeader("userId") Long userId,
            @RequestBody Map<String, Object> updates
    );

    //tạo task/event từ AI
    @PostMapping(value = "/internal/tasks/ai/create")
    TaskResponse createTaskByAI(
            @RequestHeader("userId") Long userId,
            @RequestBody TaskCreationRequest request
    );
}
