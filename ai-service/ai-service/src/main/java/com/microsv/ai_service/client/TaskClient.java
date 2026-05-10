package com.microsv.ai_service.client;

import com.microsv.ai_service.dto.response.TaskResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

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
}
