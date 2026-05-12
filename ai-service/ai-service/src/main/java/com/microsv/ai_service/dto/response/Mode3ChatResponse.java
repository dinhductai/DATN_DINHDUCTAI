package com.microsv.ai_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Mode3ChatResponse {
    private boolean structured;
    private String message;
    private String conversationId;
    private boolean canApply;
    private CreatedItem created;
    private String confirmationNote; // yêu cầu user xác nhận

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatedItem {
        private Long taskId;
        private Long eventId;
        private String type;      // "công việc" hoặc "sự kiện"
        private String title;
        private String startTime;
        private String deadline;
        private String priority;
        private String category;   // essential|work|personal|leisure
        private String status;      // TODO|IN_PROGRESS|DONE
        // Optional fields
        private String description;
        private Integer reminderMinutesBefore;
        private List<String> invitedEmails;
        // Event-only fields
        private String eventDescription;
        private Boolean isOnline;
        private String linkEvent;
        private String location;
    }
}
