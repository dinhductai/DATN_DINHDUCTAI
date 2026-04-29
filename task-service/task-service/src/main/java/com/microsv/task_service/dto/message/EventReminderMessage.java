package com.microsv.task_service.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventReminderMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Long eventId;
    private String eventDescription;
    private String linkEvent;
    private String location;
    private Boolean isOnline;
    private OffsetDateTime deadline;
}
