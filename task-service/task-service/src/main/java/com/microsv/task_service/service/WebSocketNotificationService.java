package com.microsv.task_service.service;

import com.microsv.task_service.dto.response.NotificationResponse;
import com.microsv.task_service.entity.Notification;
import com.microsv.task_service.entity.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendNotificationToUser(Long userId, Notification notification) {
        NotificationResponse response = NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .taskId(notification.getTaskId())
                .title(notification.getTitle())
                .content(notification.getContent())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();

        String destination = "/queue/notifications";
        messagingTemplate.convertAndSendToUser(userId.toString(), destination, response);
        log.info("Sent real-time notification to user {}: {}", userId, notification.getTitle());
    }

    public void sendTaskDeadlineAlert(Long userId, Task task, String message) {
        NotificationResponse response = NotificationResponse.builder()
                .userId(userId)
                .taskId(task.getTaskId())
                .title("Task Deadline Alert")
                .content(message)
                .isRead(false)
                .build();

        String destination = "/queue/notifications";
        messagingTemplate.convertAndSendToUser(userId.toString(), destination, response);
        log.info("Sent task deadline alert to user {} for task {}", userId, task.getTaskId());
    }

    public void sendTaskCreatedNotification(Long userId, Task task) {
        NotificationResponse response = NotificationResponse.builder()
                .userId(userId)
                .taskId(task.getTaskId())
                .title("New Task Created")
                .content("Task '" + task.getTitle() + "' has been assigned to you. Deadline: " + task.getDeadline())
                .isRead(false)
                .build();

        String destination = "/queue/notifications";
        messagingTemplate.convertAndSendToUser(userId.toString(), destination, response);
        log.info("Sent task created notification to user {} for task {}", userId, task.getTaskId());
    }

    public void sendTaskCompletedNotification(Long userId, Task task) {
        NotificationResponse response = NotificationResponse.builder()
                .userId(userId)
                .taskId(task.getTaskId())
                .title("Task Completed")
                .content("Task '" + task.getTitle() + "' has been marked as completed.")
                .isRead(false)
                .build();

        String destination = "/queue/notifications";
        messagingTemplate.convertAndSendToUser(userId.toString(), destination, response);
        log.info("Sent task completed notification to user {} for task {}", userId, task.getTaskId());
    }
}
