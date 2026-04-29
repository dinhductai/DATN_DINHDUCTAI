package com.microsv.task_service.scheduler;

import com.microsv.task_service.dto.EventReminderData;
import com.microsv.task_service.dto.message.EventReminderMessage;
import com.microsv.task_service.messaging.EventEmailProducer;
import com.microsv.task_service.service.EventReminderRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventReminderScheduler {

    private final EventReminderRedisService eventReminderRedisService;
    private final EventEmailProducer eventEmailProducer;

    /**
     * Poll Redis mỗi 60 giây để check các reminder đến hạn
     */
    @Scheduled(fixedRate = 60000) // 60 seconds
    public void checkDueReminders() {
        try {
            Set<Object> dueEventIds = eventReminderRedisService.getDueReminders();
            
            if (dueEventIds == null || dueEventIds.isEmpty()) {
                return;
            }

            log.info("Processing {} due reminders", dueEventIds.size());

            for (Object eventIdObj : dueEventIds) {
                Long eventId;
                try {
                    eventId = Long.parseLong(eventIdObj.toString());
                } catch (NumberFormatException e) {
                    log.error("Invalid eventId format: {}", eventIdObj);
                    continue;
                }

                processReminder(eventId);
            }
        } catch (Exception e) {
            log.error("Error in checkDueReminders scheduler: {}", e.getMessage(), e);
        }
    }

    private void processReminder(Long eventId) {
        try {
            // Lấy chi tiết event từ Redis
            EventReminderData reminderData = eventReminderRedisService.getEventReminderData(eventId);
            
            if (reminderData == null) {
                log.warn("No reminder data found for eventId: {}, removing from queue", eventId);
                eventReminderRedisService.removeEventReminder(eventId);
                return;
            }

            // Kiểm tra lại startTime đã qua chưa (event đã bắt đầu rồi thì không cần nhắc nữa)
            if (reminderData.getStartTime() != null && 
                reminderData.getStartTime().isBefore(java.time.OffsetDateTime.now())) {
                log.info("Event startTime already passed for eventId: {}, removing from queue", eventId);
                eventReminderRedisService.removeEventReminder(eventId);
                return;
            }

            // Build message và gửi qua RabbitMQ
            EventReminderMessage message = EventReminderMessage.builder()
                    .eventId(eventId)
                    .eventDescription(reminderData.getEventDescription())
                    .linkEvent(reminderData.getLinkEvent())
                    .location(reminderData.getLocation())
                    .isOnline(reminderData.getIsOnline())
                    .startTime(reminderData.getStartTime())
                    .build();

            eventEmailProducer.sendEventReminder(message);

            // Xóa reminder khỏi Redis sau khi xử lý thành công
            eventReminderRedisService.removeEventReminder(eventId);

            log.info("Successfully sent reminder for eventId: {}", eventId);

        } catch (Exception e) {
            log.error("Failed to process reminder for eventId: {}", eventId, e);
            // Không xóa reminder để retry lần sau
        }
    }
}
