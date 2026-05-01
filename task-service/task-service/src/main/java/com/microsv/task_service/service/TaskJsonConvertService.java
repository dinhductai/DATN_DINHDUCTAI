package com.microsv.task_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsv.task_service.entity.Task;
import com.microsv.task_service.enumeration.PriorityLevel;
import com.microsv.task_service.enumeration.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class TaskJsonConvertService {

    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Map<TaskStatus, String> STATUS_VI = Map.of(
            TaskStatus.TODO, "Chưa làm",
            TaskStatus.IN_PROGRESS, "Đang làm",
            TaskStatus.DONE, "Hoàn thành"
    );

    private static final Map<PriorityLevel, String> PRIORITY_VI = Map.of(
            PriorityLevel.HIGH, "Cao",
            PriorityLevel.MEDIUM, "Trung bình",
            PriorityLevel.LOW, "Thấp"
    );

    public TaskJsonConvertService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Convert list of Task entities to AI-chatbot-friendly JSON string.
     * All transformation is done locally — no external AI API call.
     */
    public String convertTasksToJson(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return buildEmptyJson();
        }

        try {
            Map<String, Object> root = new LinkedHashMap<>();

            // --- Summary section ---
            Map<String, Object> summary = buildSummary(tasks);
            root.put("summary", summary);

            // --- Task list ---
            List<Map<String, Object>> taskList = new ArrayList<>();
            for (Task task : tasks) {
                taskList.add(buildTaskEntry(task));
            }
            root.put("tasks", taskList);

            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize tasks to JSON: {}", e.getMessage());
            return buildErrorJson("Failed to convert tasks");
        }
    }

    private Map<String, Object> buildSummary(List<Task> tasks) {
        Map<String, Object> summary = new LinkedHashMap<>();

        long todoCount = tasks.stream().filter(t -> t.getStatus() == TaskStatus.TODO).count();
        long inProgressCount = tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long doneCount = tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        long overdueCount = tasks.stream()
                .filter(t -> t.getDeadline() != null
                        && t.getDeadline().isBefore(OffsetDateTime.now())
                        && t.getStatus() != TaskStatus.DONE)
                .count();

        summary.put("totalTasks", tasks.size());
        summary.put("todoCount", todoCount);
        summary.put("inProgressCount", inProgressCount);
        summary.put("doneCount", doneCount);
        summary.put("overdueCount", overdueCount);

        return summary;
    }

    private Map<String, Object> buildTaskEntry(Task task) {
        Map<String, Object> entry = new LinkedHashMap<>();

        entry.put("title", task.getTitle() != null ? task.getTitle() : "");
        entry.put("description", task.getDescription() != null ? task.getDescription() : "");

        // Status in Vietnamese
        String statusVi = STATUS_VI.getOrDefault(task.getStatus(), task.getStatus() != null ? task.getStatus().name() : "");
        entry.put("status", statusVi);
        entry.put("statusCode", task.getStatus() != null ? task.getStatus().name() : null);

        // Priority in Vietnamese
        String priorityVi = PRIORITY_VI.getOrDefault(task.getPriority(), task.getPriority() != null ? task.getPriority().name() : "");
        entry.put("priority", priorityVi);
        entry.put("priorityCode", task.getPriority() != null ? task.getPriority().name() : null);

        // Deadline formatting
        if (task.getDeadline() != null) {
            entry.put("deadline", task.getDeadline().format(DEADLINE_FORMATTER));
            entry.put("deadlineRaw", task.getDeadline().toString());

            // Friendly deadline info
            String friendlyDeadline = computeFriendlyDeadline(task.getDeadline(), task.getStatus());
            entry.put("deadlineInfo", friendlyDeadline);
        } else {
            entry.put("deadline", "Không có hạn chót");
            entry.put("deadlineRaw", null);
            entry.put("deadlineInfo", "Không có hạn chót");
        }

        // CreatedAt
        if (task.getCreatedAt() != null) {
            entry.put("createdAt", task.getCreatedAt().format(DEADLINE_FORMATTER));
            entry.put("createdAtRaw", task.getCreatedAt().toString());
        } else {
            entry.put("createdAt", null);
            entry.put("createdAtRaw", null);
        }

        // CompletedAt
        if (task.getCompletedAt() != null) {
            entry.put("completedAt", task.getCompletedAt().format(DEADLINE_FORMATTER));
            entry.put("completedAtRaw", task.getCompletedAt().toString());
        } else {
            entry.put("completedAt", task.getStatus() == TaskStatus.DONE ? "Đã hoàn thành" : "Chưa hoàn thành");
            entry.put("completedAtRaw", null);
        }

        return entry;
    }

    private String computeFriendlyDeadline(OffsetDateTime deadline, TaskStatus status) {
        if (status == TaskStatus.DONE) {
            return "Đã hoàn thành";
        }

        OffsetDateTime now = OffsetDateTime.now();
        Duration diff = Duration.between(now, deadline);

        if (diff.isNegative()) {
            long overdueDays = Math.abs(diff.toDays());
            long overdueHours = Math.abs(diff.toHours()) % 24;
            if (overdueDays > 0) {
                return String.format("Quá hạn %d ngày", overdueDays);
            } else if (overdueHours > 0) {
                return String.format("Quá hạn %d giờ", overdueHours);
            } else {
                long overdueMinutes = Math.abs(diff.toMinutes()) % 60;
                return String.format("Quá hạn %d phút", overdueMinutes);
            }
        } else {
            long days = diff.toDays();
            long hours = diff.toHours() % 24;

            if (days > 7) {
                return String.format("Còn %d ngày", days);
            } else if (days > 0) {
                return String.format("Còn %d ngày %d giờ", days, hours);
            } else if (diff.toHours() > 0) {
                return String.format("Còn %d giờ", diff.toHours());
            } else if (diff.toMinutes() > 0) {
                return String.format("Còn %d phút", diff.toMinutes());
            } else {
                return "Sắp đến hạn!";
            }
        }
    }

    private String buildEmptyJson() {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("totalTasks", 0);
            summary.put("todoCount", 0);
            summary.put("inProgressCount", 0);
            summary.put("doneCount", 0);
            summary.put("overdueCount", 0);
            root.put("summary", summary);
            root.put("tasks", List.of());
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            return "{\"summary\":{\"totalTasks\":0},\"tasks\":[]}";
        }
    }

    private String buildErrorJson(String message) {
        try {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", message);
            return objectMapper.writeValueAsString(error);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"Failed to convert tasks\"}";
        }
    }
}
