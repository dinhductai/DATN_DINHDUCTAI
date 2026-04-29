package com.microsv.task_service.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class EventUpdateRequest {
    private String eventDescription;
    private String linkEvent;
    private String location;
    private Boolean isOnline;
    private Integer reminderMinutesBefore;
    private List<String> invitedEmails;
}
