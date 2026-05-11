package com.microsv.task_service.dto.response;

import com.microsv.task_service.enumeration.PriorityLevel;
import com.microsv.task_service.enumeration.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {
    private Long taskId;
    private String title;
    private String description;
    private OffsetDateTime deadline;
    private TaskStatus status;
    private PriorityLevel priority;
    private OffsetDateTime startTime;
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;
    private Long userId;
    private Boolean isEvent;
    private Long eventId;

    // Event detail fields
    private String eventDescription;
    private String linkEvent;
    private String location;
    private Boolean isOnline;
    private Integer reminderMinutesBefore;
    private java.util.List<String> invitedEmails;
}