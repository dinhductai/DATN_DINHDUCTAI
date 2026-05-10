package com.microsv.task_service.scheduler;

import com.microsv.task_service.entity.Notification;
import com.microsv.task_service.entity.Task;
import com.microsv.task_service.enumeration.TaskStatus;
import com.microsv.task_service.repository.EventRepository;
import com.microsv.task_service.repository.NotificationRepository;
import com.microsv.task_service.repository.TaskRepository;
import com.microsv.task_service.service.WebSocketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDeadlineNotificationScheduler {

    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;
    private final WebSocketNotificationService webSocketNotificationService;
    private final EventRepository eventRepository;

    private static final Set<TaskStatus> NOTIFICATION_ELIGIBLE_STATUSES = Set.of(
            TaskStatus.TODO
    );

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm, dd/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    // Chạy mỗi 1 phút để kiểm tra task bắt đầu
    @Scheduled(fixedRate = 60000)
    public void checkAndNotifyTaskDeadlines() {
        OffsetDateTime now = OffsetDateTime.now();
        
        // Tìm task có startTime trong khoảng 1 phút trước đến hiện tại
        OffsetDateTime oneMinuteAgo = now.minusMinutes(1);
        
        List<Task> startingTasks = taskRepository.findTasksStartingNow(
                oneMinuteAgo,
                now,
                NOTIFICATION_ELIGIBLE_STATUSES
        );

        if (startingTasks.isEmpty()) {
            return;
        }

        log.info("=== Checking {} tasks that are starting ===", startingTasks.size());

        for (Task task : startingTasks) {
            Long userId = task.getUserId();
            Long taskId = task.getTaskId();

            // Chỉ gửi nếu chưa có notification cho task này
            if (!notificationRepository.existsByUserIdAndTaskId(userId, taskId)) {
                boolean isEvent = eventRepository.existsByTaskId(taskId);
                String timeStr = task.getStartTime() != null
                        ? TIME_FORMATTER.format(task.getStartTime())
                        : "";

                String title;
                String content;
                if (isEvent) {
                    title = "Sự kiện";
                    content = String.format("Sự kiện \"%s\" đã bắt đầu: %s", task.getTitle(), timeStr);
                } else {
                    title = "Công việc";
                    content = String.format("\"%s\" đã tới lúc thực hiện: %s", task.getTitle(), timeStr);
                }
                
                Notification notification = Notification.builder()
                        .userId(userId)
                        .taskId(taskId)
                        .title(title)
                        .content(content)
                        .isRead(false)
                        .build();

                notification = notificationRepository.save(notification);
                webSocketNotificationService.sendNotificationToUser(userId, notification);
                
                log.info("Sent START notification for task {} ({}) to user {}", taskId, task.getTitle(), userId);
            }
        }
    }
}
