package com.microsv.task_service.grpc.service;

import com.microsv.task_service.dto.response.TaskResponse;
import com.microsv.task_service.grpc.proto.*;
import com.microsv.task_service.service.TaskService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class GrpcTaskInternalServiceImpl extends TaskInternalGrpcServiceGrpc.TaskInternalGrpcServiceImplBase {

    private final TaskService taskService;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public void getTasks(GetTasksRequest request, StreamObserver<GetTasksResponse> responseObserver) {
        Long userId = request.getUserId();
        log.debug("gRPC GetTasks called for userId={}", userId);

        try {
            List<TaskResponse> tasks = taskService.getAllTasksByUser(userId);
            GetTasksResponse response = GetTasksResponse.newBuilder()
                    .addAllTasks(tasks.stream().map(this::toProto).toList())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in gRPC GetTasks for userId={}: {}", userId, e.getMessage(), e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Failed to get tasks: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    private TaskProto toProto(TaskResponse r) {
        TaskProto.Builder builder = TaskProto.newBuilder()
                .setTaskId(r.getTaskId() != null ? r.getTaskId() : 0L)
                .setTitle(r.getTitle() != null ? r.getTitle() : "")
                .setDescription(r.getDescription() != null ? r.getDescription() : "")
                .setStatus(toGrpcStatus(r.getStatus()))
                .setPriority(toGrpcPriority(r.getPriority()))
                .setUserId(r.getUserId() != null ? r.getUserId() : 0L)
                .setIsEvent(r.getIsEvent() != null && r.getIsEvent());

        if (r.getDeadline() != null) {
            builder.setDeadline(r.getDeadline().format(ISO_FORMATTER));
        }
        if (r.getStartTime() != null) {
            builder.setStartTime(r.getStartTime().format(ISO_FORMATTER));
        }
        if (r.getCreatedAt() != null) {
            builder.setCreatedAt(r.getCreatedAt().format(ISO_FORMATTER));
        }
        if (r.getCompletedAt() != null) {
            builder.setCompletedAt(r.getCompletedAt().format(ISO_FORMATTER));
        }
        if (r.getIsEvent() != null && r.getIsEvent()) {
            if (r.getEventDescription() != null) builder.setEventDescription(r.getEventDescription());
            if (r.getLinkEvent() != null) builder.setLinkEvent(r.getLinkEvent());
            if (r.getLocation() != null) builder.setLocation(r.getLocation());
            if (r.getIsOnline() != null) builder.setIsOnline(r.getIsOnline());
            if (r.getReminderMinutesBefore() != null) builder.setReminderMinutesBefore(r.getReminderMinutesBefore());
            if (r.getEventId() != null) builder.setEventId(r.getEventId());
            if (r.getInvitedEmails() != null) builder.addAllInvitedEmails(r.getInvitedEmails());
        }

        return builder.build();
    }

    private GrpcTaskStatus toGrpcStatus(com.microsv.task_service.enumeration.TaskStatus status) {
        if (status == null) return GrpcTaskStatus.TASK_STATUS_UNSPECIFIED;
        return switch (status) {
            case TODO -> GrpcTaskStatus.TODO;
            case IN_PROGRESS -> GrpcTaskStatus.IN_PROGRESS;
            case DONE -> GrpcTaskStatus.DONE;
            case CANCELLED -> GrpcTaskStatus.CANCELLED;
        };
    }

    private GrpcPriorityLevel toGrpcPriority(com.microsv.task_service.enumeration.PriorityLevel priority) {
        if (priority == null) return GrpcPriorityLevel.PRIORITY_UNSPECIFIED;
        return switch (priority) {
            case LOW -> GrpcPriorityLevel.LOW;
            case MEDIUM -> GrpcPriorityLevel.MEDIUM;
            case HIGH -> GrpcPriorityLevel.HIGH;
        };
    }
}
