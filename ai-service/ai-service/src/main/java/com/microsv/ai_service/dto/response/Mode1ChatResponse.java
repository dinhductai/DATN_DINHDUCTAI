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
public class Mode1ChatResponse {
    private boolean structured;
    private String message;
    private String answerType;

    private Summary summary;
    private List<TaskItem> tasks;
    private List<EventItem> events;
    private Highlight highlight;

    private String conversationId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private int totalTasks;
        private int todoCount;
        private int inProgressCount;
        private int doneCount;
        private int overdueCount;
        private int eventCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskItem {
        private Long taskId;
        private String title;
        private String deadline;
        private String deadlineInfo;
        private String priority;
        private String status;
        private Boolean isEvent;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventItem {
        private Long taskId;
        private Long eventId;
        private String title;
        private String startTime;
        private String location;
        private Boolean isOnline;
        private String priority;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Highlight {
        private String mostUrgent;
        private String mostImportant;
    }
}
