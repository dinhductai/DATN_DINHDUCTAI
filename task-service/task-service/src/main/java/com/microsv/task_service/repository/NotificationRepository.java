package com.microsv.task_service.repository;

import com.microsv.task_service.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findAllByUserIdAndIsReadOrderByCreatedAtDesc(Long userId, Boolean isRead);
    Long countByUserIdAndIsRead(Long userId, Boolean isRead);
}
