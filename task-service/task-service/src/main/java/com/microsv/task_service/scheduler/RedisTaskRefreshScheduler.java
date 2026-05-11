package com.microsv.task_service.scheduler;

import com.microsv.task_service.feign.UserClient;
import com.microsv.task_service.service.TaskCacheService;
import com.microsv.task_service.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTaskRefreshScheduler {

    private final UserClient userClient;
    private final TaskService taskService;

    // @Scheduled(fixedRate = 60000)
    public void refreshAllUsersTaskCache() {
        try {
            List<Long> userIds = userClient.getAllUserIds();
            if (userIds == null || userIds.isEmpty()) {
                log.debug("No users found for Redis refresh");
                return;
            }

            int successCount = 0;
            int failCount = 0;

            for (Long userId : userIds) {
                try {
                    taskService.syncTasksToCache(userId);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.warn("Failed to refresh Redis cache for user {}: {}", userId, e.getMessage());
                }
            }

            log.info("Redis task cache refresh completed: {} success, {} failed out of {} total users",
                    successCount, failCount, userIds.size());

        } catch (Exception e) {
            log.error("Failed to refresh Redis task cache: {}", e.getMessage());
        }
    }
}
