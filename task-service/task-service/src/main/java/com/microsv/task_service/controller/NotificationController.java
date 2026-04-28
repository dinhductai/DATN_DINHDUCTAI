package com.microsv.task_service.controller;

import com.microsv.task_service.dto.request.SubscriptionRequest;
import com.microsv.task_service.entity.PushSubscription;
import com.microsv.task_service.service.NotificationService;
import com.microsv.task_service.service.DailyTaskNotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationController {

    private final NotificationService notificationService;
    private final DailyTaskNotificationService dailyTaskNotificationService;


    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(@RequestBody SubscriptionRequest request, @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        notificationService.subscribe(request, userId);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PostMapping("/trigger-daily")
    public ResponseEntity<String> triggerDailyNotification() {
        log.info("Manually triggering daily task notification");
        dailyTaskNotificationService.sendDailyTaskNotifications();
        return ResponseEntity.ok("Daily task notification triggered successfully");
    }

//    @DeleteMapping("/unsubscribe")
//    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal Jwt jwt) {
//        Long userId = Long.parseLong(jwt.getSubject());
//        notificationService.unsubscribe(userId);
//        log.info("User {} unsubscribed from push notifications", userId);
//        return new ResponseEntity<>(HttpStatus.OK);
//    }
}