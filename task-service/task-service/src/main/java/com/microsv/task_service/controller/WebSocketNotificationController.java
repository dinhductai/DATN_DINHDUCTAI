package com.microsv.task_service.controller;

import com.microsv.task_service.scheduler.TaskDeadlineNotificationScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class WebSocketNotificationController {

    private final TaskDeadlineNotificationScheduler scheduler;

    @PostMapping("/trigger-deadline-check")
    public ResponseEntity<Map<String, String>> triggerDeadlineCheck() {
        log.info("Manual trigger for deadline check initiated");
        scheduler.checkAndNotifyTaskDeadlines();
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Deadline check triggered successfully"
        ));
    }
}
