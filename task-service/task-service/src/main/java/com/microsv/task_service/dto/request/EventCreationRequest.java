package com.microsv.task_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class EventCreationRequest {
    
    @NotBlank(message = "Event description is required")
    private String eventDescription;
    
    private String linkEvent;
    
    private String location;
    
    private Boolean isOnline;
    
    private Integer reminderMinutesBefore;
    
    private List<String> invitedEmails;
}
