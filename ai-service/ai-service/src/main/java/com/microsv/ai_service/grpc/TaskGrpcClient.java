package com.microsv.ai_service.grpc;

import com.microsv.ai_service.dto.response.TaskResponse;
import com.microsv.ai_service.enumeration.PriorityLevel;
import com.microsv.ai_service.enumeration.TaskStatus;
import com.microsv.ai_service.grpc.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class TaskGrpcClient {

    private final ManagedChannel channel;
    private final TaskInternalGrpcServiceGrpc.TaskInternalGrpcServiceBlockingStub stub;
    private final boolean enabled;

    public TaskGrpcClient(
            @Value("${grpc.task-service.enabled:false}") boolean enabled,
            @Value("${grpc.task-service.host:task-service}") String host,
            @Value("${grpc.task-service.port:9090}") int port) {
        this.enabled = enabled;

        if (enabled) {
            this.channel = ManagedChannelBuilder
                    .forAddress(host, port)
                    .usePlaintext()
                    .build();
            this.stub = TaskInternalGrpcServiceGrpc.newBlockingStub(channel);
            log.info("gRPC client connected to task-service at {}:{}", host, port);
        } else {
            this.channel = null;
            this.stub = null;
            log.info("gRPC client disabled, using REST (Feign)");
        }
    }

    /**
     * Get all tasks for a user via gRPC.
     * Returns null if gRPC is disabled — caller should fall back to REST.
     */
    public List<TaskResponse> getTasks(Long userId) {
        if (!enabled) {
            log.debug("gRPC disabled, returning null (use REST fallback)");
            return null;
        }

        try {
            GetTasksRequest request = GetTasksRequest.newBuilder()
                    .setUserId(userId != null ? userId : 0L)
                    .build();

            GetTasksResponse response = stub.getTasks(request);
            return response.getTasksList().stream()
                    .map(this::toTaskResponse)
                    .toList();
        } catch (Exception e) {
            log.error("gRPC getTasks failed for userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    private TaskResponse toTaskResponse(TaskProto proto) {
        return TaskResponse.builder()
                .taskId(proto.getTaskId() != 0 ? proto.getTaskId() : null)
                .title(proto.getTitle())
                .description(proto.getDescription().isEmpty() ? null : proto.getDescription())
                .deadline(parseOffsetDateTime(proto.getDeadline()))
                .status(toTaskStatus(proto.getStatus()))
                .priority(toPriorityLevel(proto.getPriority()))
                .startTime(parseOffsetDateTime(proto.getStartTime()))
                .createdAt(parseOffsetDateTime(proto.getCreatedAt()))
                .completedAt(parseOffsetDateTime(proto.getCompletedAt()))
                .userId(proto.getUserId() != 0 ? proto.getUserId() : null)
                .build();
    }

    private OffsetDateTime parseOffsetDateTime(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            return OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    private TaskStatus toTaskStatus(GrpcTaskStatus status) {
        if (status == null) return null;
        return switch (status) {
            case TODO -> TaskStatus.TODO;
            case IN_PROGRESS -> TaskStatus.IN_PROGRESS;
            case DONE -> TaskStatus.DONE;
            case CANCELLED -> TaskStatus.CANCELLED;
            default -> null;
        };
    }

    private PriorityLevel toPriorityLevel(GrpcPriorityLevel priority) {
        if (priority == null) return null;
        return switch (priority) {
            case LOW -> PriorityLevel.LOW;
            case MEDIUM -> PriorityLevel.MEDIUM;
            case HIGH -> PriorityLevel.HIGH;
            default -> null;
        };
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            log.info("gRPC channel shut down");
        }
    }
}
