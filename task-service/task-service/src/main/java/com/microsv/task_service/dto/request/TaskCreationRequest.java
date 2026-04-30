package com.microsv.task_service.dto.request;

import com.microsv.task_service.enumeration.PriorityLevel;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class TaskCreationRequest {
    private String title;
    private String description;
    private OffsetDateTime startTime;
    private OffsetDateTime deadline;
    private PriorityLevel priority;

    private Boolean isEvent;
    private EventCreationRequest eventCreationRequest;
}