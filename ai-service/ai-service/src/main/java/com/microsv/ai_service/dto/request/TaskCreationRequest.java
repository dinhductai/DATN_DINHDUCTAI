package com.microsv.ai_service.dto.request;

import com.microsv.ai_service.enumeration.TaskStatus;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class TaskCreationRequest {
    private String title;
    private String description;
    private OffsetDateTime startTime;
    private OffsetDateTime deadline;
    private String priority; // "HIGH", "MEDIUM", "LOW"
    private TaskStatus status; // TODO, IN_PROGRESS, DONE

    private Boolean isEvent;
    private EventCreationRequest eventCreationRequest;
}
