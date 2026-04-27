package com.microsv.task_service.mapper;

import com.microsv.task_service.dto.request.TaskCreationRequest;
import com.microsv.task_service.dto.response.*;
import com.microsv.task_service.entity.Task;
import com.microsv.task_service.enumeration.PriorityLevel;
import com.microsv.task_service.enumeration.TaskStatus;
import com.microsv.task_service.util.EnumUtil;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {
    public Task taskCreationRequestToTask(TaskCreationRequest request, Long userId){
        return Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .deadline(request.getDeadline())
                .createdAt(request.getStartTime())
                .priority(request.getPriority() != null ? request.getPriority() : PriorityLevel.MEDIUM)
                .status(TaskStatus.TODO)
                .userId(userId)
                .build();
    }

    public TaskResponse toTaskResponse(Task task) {
        return TaskResponse.builder()
                .taskId(task.getTaskId())
                .title(task.getTitle())
                .description(task.getDescription())
                .deadline(task.getDeadline())
                .status(task.getStatus())
                .priority(task.getPriority())
                .createdAt(task.getCreatedAt())
                .completedAt(task.getCompletedAt())
                .userId(task.getUserId())
                .build();
    }

    public TaskResponse tupleToTaskResponse(Tuple tuple) {
        return TaskResponse.builder()
                .taskId(tuple.get("taskId", Long.class))
                .title(tuple.get("title", String.class))
                .description(tuple.get("description", String.class))
                .deadline(toOffsetDateTime(tuple.get("deadline")))
                .status(EnumUtil.convertStatus(tuple.get("status")))
                .priority(EnumUtil.convertPriority(tuple.get("priority")))
                .createdAt(toOffsetDateTime(tuple.get("createdAt")))
                .completedAt(toOffsetDateTime(tuple.get("completedAt")))
                .userId(tuple.get("userId", Long.class))
                .build();
    }

    private java.time.OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof java.time.OffsetDateTime) return (java.time.OffsetDateTime) value;
        if (value instanceof java.time.Instant) return ((java.time.Instant) value).atOffset(java.time.ZoneOffset.UTC);
        if (value instanceof java.sql.Timestamp) return ((java.sql.Timestamp) value).toInstant().atOffset(java.time.ZoneOffset.UTC);
        // Fallback: try to convert string
        if (value instanceof String) {
            try {
                return java.time.OffsetDateTime.parse((String) value);
            } catch (Exception ignored) {}
        }
        return null;
    }

    public StatusTaskWeekResponse tupleToStatusTaskWeekResponse(Tuple tuple){
        return StatusTaskWeekResponse.builder()
                .completedRate(tuple.get("completedRate", Long.class))
                .inProgressRate(tuple.get("inProgressRate", Long.class))
                .todoRate(tuple.get("todoRate", Long.class))
                .build();
    }

    public DailyTaskCountResponse tupleToDailyTaskCountResponse(Tuple tuple){
        return DailyTaskCountResponse.builder()
                .dayName(tuple.get("day_name", String.class))
                .taskCount(tuple.get("task_count", Long.class))
                .build();
    }

    public TaskTimelineResponse tupleToTaskTimelineResponse(Tuple tuple){
        return TaskTimelineResponse.builder()
                .weekLabel(tuple.get("week_label", String.class))
                .taskCount(tuple.get("task_count", Long.class))
                .build();
    }

    public DailyCompletedTasksResponse tupleToDailyCompletedTasksResponse(Tuple tuple){
        return DailyCompletedTasksResponse.builder()
                .dayName(tuple.get("day_name", String.class))
                .completedCount(tuple.get("completed_count", Long.class))
                .build();
    }

    public TaskPriorityCountResponse tupleToTaskPriorityCountResponse(Tuple tuple){
        return TaskPriorityCountResponse.builder()
                .priorityLevel(tuple.get("priority_level",String.class))
                .taskCount(tuple.get("task_count", Long.class))
                .build();
    }

}
