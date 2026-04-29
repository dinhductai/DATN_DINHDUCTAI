package com.microsv.email_service.messaging;

import com.microsv.email_service.config.RabbitMQConfig;
import com.microsv.email_service.dto.message.EventUpdateMessage;
import com.microsv.email_service.service.EventInvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventUpdateListener {
    
    private final EventInvitationService eventInvitationService;
    
    @RabbitListener(queues = RabbitMQConfig.EVENT_UPDATE_QUEUE)
    public void handleEventUpdate(EventUpdateMessage message) {
        log.info("Received event update for eventId: {}", message.getEventId());
        
        try {
            eventInvitationService.updateInvitations(message);
            log.info("Event update processed successfully for eventId: {}", message.getEventId());
        } catch (Exception e) {
            log.error("Failed to process event update for eventId {}: {}", message.getEventId(), e.getMessage());
        }
    }
}
