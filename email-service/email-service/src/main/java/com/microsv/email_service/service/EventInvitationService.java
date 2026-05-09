package com.microsv.email_service.service;

import com.microsv.email_service.dto.message.EventDeleteMessage;
import com.microsv.email_service.dto.message.EventReminderMessage;
import com.microsv.email_service.dto.message.EventUpdateMessage;
import com.microsv.email_service.entity.EventInvitation;
import com.microsv.email_service.repository.EventInvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            invitation.setSentAt(OffsetDateTime.now());
            eventInvitationRepository.save(invitation);
        }
        
        log.info("Sent {} reminder emails for eventId: {}", invitations.size(), message.getEventId());
    }
    
    @Transactional
    public void updateInvitations(EventUpdateMessage message) {
        Long eventId = message.getEventId();
        List<String> newEmails = message.getInvitedEmails();
        
        if (newEmails == null || newEmails.isEmpty()) {
            log.info("No invitedEmails in update message for eventId: {}, skipping update", eventId);
            return;
        }
        
        List<EventInvitation> existingInvitations = eventInvitationRepository.findByEventId(eventId);
        Set<String> existingEmails = new HashSet<>();
        for (EventInvitation inv : existingInvitations) {
            existingEmails.add(inv.getInvitedEmail().toLowerCase());
        }
        
        Set<String> newEmailSet = new HashSet<>();
        for (String email : newEmails) {
            newEmailSet.add(email.toLowerCase());
        }
        
        if (existingEmails.equals(newEmailSet)) {
            log.info("InvitedEmails are unchanged for eventId: {}, skipping update", eventId);
            return;
        }
        
        log.info("InvitedEmails changed for eventId: {}. Old: {}, New: {}. Updating...", 
                eventId, existingEmails, newEmailSet);
        
        eventInvitationRepository.deleteAll(existingInvitations);
        
        for (String email : newEmails) {
            EventInvitation invitation = EventInvitation.builder()
                    .eventId(eventId)
                    .invitedEmail(email)
                    .status("PENDING")
                    .build();
            eventInvitationRepository.save(invitation);
        }
        
        log.info("Updated {} invitations for eventId: {}", newEmails.size(), eventId);
    }
    
    @Transactional
    public void deleteInvitations(EventDeleteMessage message) {
        Long eventId = message.getEventId();
        List<EventInvitation> invitations = eventInvitationRepository.findByEventId(eventId);
        
        if (invitations.isEmpty()) {
            log.info("No invitations found for eventId: {}, nothing to delete", eventId);
            return;
        }
        
        eventInvitationRepository.deleteAll(invitations);
        log.info("Deleted {} invitations for eventId: {}", invitations.size(), eventId);
    }
}
