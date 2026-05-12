package com.microsv.task_service.service;

import com.microsv.task_service.dto.response.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationService {
    List<NotificationResponse> getAllNotifications(Long userId);
    List<NotificationResponse> getUnreadNotifications(Long userId);
    Long getUnreadCount(Long userId);
    void markAsRead(Long notificationId, Long userId);
    void markAllAsRead(Long userId);
    Page<NotificationResponse> getNotifications(Long userId, Pageable pageable);
    Page<NotificationResponse> getUnreadNotifications(Long userId, Pageable pageable);
}
