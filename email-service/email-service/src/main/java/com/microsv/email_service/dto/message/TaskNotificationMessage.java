package com.microsv.email_service.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskNotificationMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Long userId;
    private String userEmail;
    private String userName;
    private List<TaskInfo> tasks;
    private String date;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private Long taskId;
        private String title;
        private String description;
        private String priority;
        private OffsetDateTime deadline;
        private String status;
    }
}
