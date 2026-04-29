package com.microsv.email_service.service;

import com.microsv.email_service.dto.message.EventReminderMessage;
import com.microsv.email_service.entity.EventInvitation;
import com.microsv.email_service.repository.EventInvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventInvitationService {
    
    private final EventInvitationRepository eventInvitationRepository;
    private final EmailService emailService;
    
    @Transactional
    public void saveInvitations(Long eventId, List<String> invitedEmails) {
        for (String email : invitedEmails) {
            EventInvitation invitation = EventInvitation.builder()
                    .eventId(eventId)
                    .invitedEmail(email)
                    .status("PENDING")
                    .build();
            eventInvitationRepository.save(invitation);
        }
        log.info("Saved {} invitations for eventId: {}", invitedEmails.size(), eventId);
    }
    
    @Transactional
    public void sendInvitations(EventReminderMessage message) {
        List<EventInvitation> invitations = eventInvitationRepository.findByEventId(message.getEventId());
        
        for (EventInvitation invitation : invitations) {
            emailService.sendEventReminderEmail(invitation.getInvitedEmail(), message);
            invitation.setStatus("SENT");
            invitation.setSentAt(LocalDateTime.now());
            eventInvitationRepository.save(invitation);
        }
        
        log.info("Sent {} reminder emails for eventId: {}", invitations.size(), message.getEventId());
    }
}
