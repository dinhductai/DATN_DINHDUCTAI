package com.microsv.email_service.listener;

import com.microsv.email_service.config.RabbitMQConfig;
import com.microsv.email_service.dto.message.EventCreationMessage;
import com.microsv.email_service.dto.message.EventReminderMessage;
import com.microsv.email_service.service.EventInvitationService;
import com.microsv.email_service.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventEmailListener {
    
    private final EventInvitationService eventInvitationService;
    private final EmailService emailService;
    
    // Lúc tạo event: chỉ lưu invited emails vào DB
    @RabbitListener(queues = RabbitMQConfig.EVENT_CREATION_QUEUE)
    public void handleEventCreation(EventCreationMessage message) {
        log.info("Received event creation message - eventId: {}, invitedEmails count: {}", 
                message.getEventId(), 
                message.getInvitedEmails() != null ? message.getInvitedEmails().size() : 0);
        
        try {
            if (message.getInvitedEmails() != null && !message.getInvitedEmails().isEmpty()) {
                eventInvitationService.saveInvitations(message.getEventId(), message.getInvitedEmails());
                log.info("Successfully saved invitations for eventId: {}", message.getEventId());
            }
        } catch (Exception e) {
            log.error("Failed to save invitations for eventId {}: {}", message.getEventId(), e.getMessage());
        }
    }
    
    // Lúc reminder đến hạn: lấy invited emails từ DB và gửi email
    @RabbitListener(queues = RabbitMQConfig.EVENT_REMINDER_QUEUE)
    public void handleEventReminder(EventReminderMessage message) {
        log.info("Received event reminder message - eventId: {}", message.getEventId());
        
        try {
            eventInvitationService.sendInvitations(message);
            log.info("Successfully sent reminder emails for eventId: {}", message.getEventId());
        } catch (Exception e) {
            log.error("Failed to send reminder for eventId {}: {}", message.getEventId(), e.getMessage());
        }
    }
}
