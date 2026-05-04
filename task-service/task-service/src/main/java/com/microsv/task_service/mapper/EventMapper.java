package com.microsv.task_service.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsv.task_service.dto.request.EventCreationRequest;
import com.microsv.task_service.entity.Event;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EventMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Event toEvent(EventCreationRequest request, Long taskId) {
        return Event.builder()
                .taskId(taskId)
                .eventDescription(request.getEventDescription())
                .linkEvent(request.getLinkEvent())
                .location(request.getLocation())
                .isOnline(request.getIsOnline() != null ? request.getIsOnline() : false)
                .reminderMinutesBefore(request.getReminderMinutesBefore() != null ? request.getReminderMinutesBefore() : 30)
                .invitedEmails(listToJson(request.getInvitedEmails()))
                .build();
    }

    public void updateEvent(Event event, com.microsv.task_service.dto.request.EventUpdateRequest request) {
        if (request.getEventDescription() != null) {
            event.setEventDescription(request.getEventDescription());
        }
        if (request.getLinkEvent() != null) {
            event.setLinkEvent(request.getLinkEvent());
        }
        if (request.getLocation() != null) {
            event.setLocation(request.getLocation());
        }
        if (request.getIsOnline() != null) {
            event.setIsOnline(request.getIsOnline());
        }
        if (request.getReminderMinutesBefore() != null) {
            event.setReminderMinutesBefore(request.getReminderMinutesBefore());
        }
        if (request.getInvitedEmails() != null) {
            event.setInvitedEmails(listToJson(request.getInvitedEmails()));
        }
    }

    public List<String> jsonToList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String listToJson(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
