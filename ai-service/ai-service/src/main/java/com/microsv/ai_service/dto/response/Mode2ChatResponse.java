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
public class Mode2ChatResponse {
    private boolean structured;
    private String message;
    private String conversationId;
    private List<ScheduledItem> schedule;
    private List<String> advice;
    private boolean canApply;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduledItem {
        private Long taskId;
        private Long eventId;
        private String type;
        private String title;
        private String startTime;
        private String deadline;
        private String category;
    }
}
