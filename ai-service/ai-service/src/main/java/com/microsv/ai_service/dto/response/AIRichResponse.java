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
public class AIRichResponse {
    private boolean structured;
    private String message;
    private SummaryData summary;
    private List<TaskItem> tasks;
    private List<RecommendationItem> recommendations;
    private String motivation;
    private String followUp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryData {
        private int totalTasks;
        private int pendingTasks;
        private int overdueTasks;
        private int completedToday;
        private double completionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskItem {
        private String emoji;
        private Long taskId;
        private String title;
        private String description;
        private String deadline;
        private String priority;
        private String status;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationItem {
        private Long taskId;
        private String taskTitle;
        private String reason;
        private int order;
    }

    private String conversationId;
}
