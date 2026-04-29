package com.microsv.task_service.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventUpdateMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Long eventId;
    private List<String> invitedEmails;
}
