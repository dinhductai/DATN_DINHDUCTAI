package com.microsv.email_service.messaging;

import com.microsv.email_service.config.RabbitMQConfig;
import com.microsv.email_service.dto.message.EventDeleteMessage;
import com.microsv.email_service.service.EventInvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventDeleteListener {
    
    private final EventInvitationService eventInvitationService;
    
    @RabbitListener(queues = RabbitMQConfig.EVENT_DELETE_QUEUE)
    public void handleEventDelete(EventDeleteMessage message) {
        log.info("Received event delete for eventId: {}", message.getEventId());
        
        try {
            eventInvitationService.deleteInvitations(message);
            log.info("Event delete processed successfully for eventId: {}", message.getEventId());
        } catch (Exception e) {
            log.error("Failed to process event delete for eventId {}: {}", message.getEventId(), e.getMessage());
        }
    }
}
