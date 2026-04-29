package com.microsv.task_service.messaging;

import com.microsv.task_service.dto.message.EventCreationMessage;
import com.microsv.task_service.dto.message.EventReminderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventEmailProducer {
    
    private final RabbitTemplate rabbitTemplate;
    
    // Gửi message khi tạo event: chỉ gửi eventId + invitedEmails để lưu vào DB
    public void sendEventCreation(EventCreationMessage message) {
        try {
            log.info("Sending event creation message to RabbitMQ for eventId: {}, invitedEmails count: {}", 
                    message.getEventId(), 
                    message.getInvitedEmails() != null ? message.getInvitedEmails().size() : 0);
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EVENT_CREATION_QUEUE,
                message
            );
            log.info("Event creation message sent successfully for eventId: {}", message.getEventId());
        } catch (Exception e) {
            log.error("Failed to send event creation message for eventId {}: {}", message.getEventId(), e.getMessage());
        }
    }
    
    // Gửi message khi reminder đến hạn: gửi đầy đủ thông tin để email service gửi email
    public void sendEventReminder(EventReminderMessage message) {
        try {
            log.info("Sending event reminder message to RabbitMQ for eventId: {}", message.getEventId());
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EVENT_REMINDER_QUEUE,
                message
            );
            log.info("Event reminder message sent successfully for eventId: {}", message.getEventId());
        } catch (Exception e) {
            log.error("Failed to send event reminder message for eventId {}: {}", message.getEventId(), e.getMessage());
        }
    }
}
