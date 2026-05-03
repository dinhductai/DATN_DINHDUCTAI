package com.microsv.task_service.service;

import com.microsv.task_service.dto.message.TaskNotificationMessage;
import com.microsv.task_service.dto.message.TaskNotificationMessage.TaskInfo;
import com.microsv.task_service.entity.Notification;
import com.microsv.task_service.entity.Task;
import com.microsv.task_service.enumeration.TaskStatus;
import com.microsv.task_service.feign.UserClient;
import com.microsv.task_service.messaging.TaskNotificationProducer;
import com.microsv.task_service.repository.NotificationRepository;
import com.microsv.task_service.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyTaskNotificationService {

    private final TaskRepository taskRepository;
    private final UserClient userClient;
    private final TaskNotificationProducer taskNotificationProducer;
    private final NotificationRepository notificationRepository;

    // @Scheduled(cron = "0 55 4 * * *", zone = "Asia/Ho_Chi_Minh")
    public void sendDailyTaskNotifications() {
        log.info("Starting daily task notification job");

        try {
            OffsetDateTime startOfDay = OffsetDateTime.of(
                    LocalDateTime.of(LocalDate.now(), LocalTime.MIN), ZoneOffset.UTC);
            OffsetDateTime endOfDay = OffsetDateTime.of(
                    LocalDateTime.of(LocalDate.now(), LocalTime.MAX), ZoneOffset.UTC);

            List<Task> todayTasks = taskRepository.findAllByDeadlineBetweenAndStatus(
                    startOfDay,
                    endOfDay,
                    TaskStatus.TODO
            );

            if (todayTasks.isEmpty()) {
                log.info("No tasks due today, skipping notification");
                return;
            }

            Map<Long, List<Task>> tasksByUser = todayTasks.stream()
                    .collect(Collectors.groupingBy(Task::getUserId));

            for (Map.Entry<Long, List<Task>> entry : tasksByUser.entrySet()) {
                Long userId = entry.getKey();
                List<Task> userTasks = entry.getValue();

                try {
                    String userEmail = userClient.getUserEmail(userId);

                    List<TaskInfo> taskInfos = userTasks.stream()
                            .map(this::convertToTaskInfo)
                            .collect(Collectors.toList());

                    TaskNotificationMessage message = TaskNotificationMessage.builder()
                            .userId(userId)
                            .userEmail(userEmail)
                            .tasks(taskInfos)
                            .date(LocalDate.now().toString())
                            .build();

                    taskNotificationProducer.sendTaskNotification(message);

                    saveNotification(userId, userTasks);
                    log.info("Sent notification to user {} with {} tasks", userId, userTasks.size());

                } catch (Exception e) {
                    log.error("Failed to send notification for user {}: {}", userId, e.getMessage());
                }
            }

            log.info("Daily task notification job completed");

        } catch (Exception e) {
            log.error("Error in daily task notification job: {}", e.getMessage());
        }
    }

    private void saveNotification(Long userId, List<Task> tasks) {
        String title = "Bạn có " + tasks.size() + " công việc đến hạn hôm nay";
        String content = String.join("\n", tasks.stream()
                .map(t -> "- " + t.getTitle())
                .toList());

        Notification notification = Notification.builder()
                .userId(userId)
                .taskId(tasks.get(0).getTaskId())
                .title(title)
                .content(content)
                .isRead(false)
                .build();

        notificationRepository.save(notification);
    }

    private TaskInfo convertToTaskInfo(Task task) {
        return TaskInfo.builder()
                .taskId(task.getTaskId())
                .title(task.getTitle())
                .description(task.getDescription())
                .priority(task.getPriority().name())
                .deadline(task.getDeadline())
                .status(task.getStatus().name())
                .build();
    }
}
