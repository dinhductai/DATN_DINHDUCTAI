package com.microsv.ai_service.dto.request;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class EventCreationRequest {
    private String eventDescription;
    private String linkEvent;
    private String location;
    private Boolean isOnline;
    private Integer reminderMinutesBefore;
    private List<String> invitedEmails;
    private OffsetDateTime startTime;
}
