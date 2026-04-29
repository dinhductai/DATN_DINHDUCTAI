package com.microsv.task_service.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDeleteMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Long eventId;
}
