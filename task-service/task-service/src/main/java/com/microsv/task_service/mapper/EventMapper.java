package com.microsv.task_service.mapper;

import com.microsv.task_service.dto.request.EventCreationRequest;
import com.microsv.task_service.entity.Event;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {
    
    public Event toEvent(EventCreationRequest request, Long taskId) {
        return Event.builder()
                .taskId(taskId)
                .eventDescription(request.getEventDescription())
                .linkEvent(request.getLinkEvent())
                .location(request.getLocation())
                .isOnline(request.getIsOnline() != null ? request.getIsOnline() : false)
                .reminderMinutesBefore(request.getReminderMinutesBefore() != null ? request.getReminderMinutesBefore() : 30)
                .build();
    }
}
