package com.microsv.task_service.service.impl;

import com.microsv.common.enumeration.ErrorCode;
import com.microsv.common.exception.BaseException;
import com.microsv.task_service.dto.request.SubscriptionRequest;
import com.microsv.task_service.entity.PushSubscription;
import com.microsv.task_service.entity.Task;
import com.microsv.task_service.enumeration.TaskStatus;
import com.microsv.task_service.mapper.NotificationMapper;
import com.microsv.task_service.repository.PushSubscriptionRepository;
import com.microsv.task_service.repository.TaskRepository;
import com.microsv.task_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    // constructor-injected: final required
    private final TaskRepository taskRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final NotificationMapper notificationMapper;

    // field-injected by @Value — NOT final, reassigned in @PostConstruct
    @Value("${vapid.public.key:#{null}}")
    private String publicKey;

    @Value("${vapid.private.key:#{null}}")
    private String privateKey;

    // lazy-initialized in @PostConstruct — NOT final
    private PushService pushService;
    private boolean pushServiceInitialized = false;

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            if (publicKey == null || publicKey.isBlank()
                    || privateKey == null || privateKey.isBlank()) {
                log.warn("VAPID keys not configured — push notifications are DISABLED. " +
                        "Set vapid.public.key and vapid.private.key to enable.");
                return;
            }

            pushService = new PushService(publicKey, privateKey, "mailto:dinhductai2501@gmail.com");
            pushServiceInitialized = true;
            log.info("PushService initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize PushService — push notifications DISABLED. " +
                    "Check VAPID keys: {}", e.getMessage());
            // DON'T rethrow — allow app to start
        }
    }

    @Override
    @Scheduled(fixedRate = 30000)
    public void checkDeadlinesAndSendNotifications() {
        if (!pushServiceInitialized) {
            log.debug("PushService not initialized — skipping deadline check");
            return;
        }

        try {
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime inOneHour = now.plusHours(1);

            List<Task> upcomingTasks = taskRepository
                    .findAllByDeadlineBetweenAndStatus(now, inOneHour, TaskStatus.TODO);

            for (Task task : upcomingTasks) {
                List<PushSubscription> subscriptions = subscriptionRepository
                        .findAllByUserId(task.getUserId());

                for (PushSubscription sub : subscriptions) {
                    String payload = String.format("Task '%s' is due soon!", task.getTitle());
                    sendNotification(sub, payload);
                }
            }
        } catch (Exception e) {
            log.error("Error during deadline check: {}", e.getMessage());
        }
    }

    @Override
    public void subscribe(SubscriptionRequest request, Long userId) {
        try {
            subscriptionRepository.save(notificationMapper.toPushSubscription(request, userId));
        } catch (Exception e) {
            throw new BaseException(ErrorCode.DATABASE_QUERY_ERROR);
        }
    }

    public void sendNotification(PushSubscription subscription, String payload) {
        if (!pushServiceInitialized) {
            log.debug("PushService not initialized — skipping notification");
            return;
        }

        try {
            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dh(),
                    subscription.getAuth(),
                    payload
            );

            pushService.send(notification);
        } catch (Exception e) {
            log.warn("Failed to send push notification to endpoint {}: {}",
                    subscription.getEndpoint(), e.getMessage());
        }
    }
}
