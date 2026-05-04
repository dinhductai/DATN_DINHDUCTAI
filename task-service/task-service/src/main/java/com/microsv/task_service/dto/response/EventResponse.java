package com.microsv.task_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {
    private Long eventId;
    private Long taskId;
    private String title;
    private String description;
    private OffsetDateTime startTime;
    private OffsetDateTime deadline;
    private String status;
    private String priority;
    private String eventDescription;
    private String linkEvent;
    private String location;
    private Boolean isOnline;
    private Integer reminderMinutesBefore;
    private java.util.List<String> invitedEmails;
}
