package com.microsv.ai_service.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsv.ai_service.client.TaskClient;
import com.microsv.ai_service.dto.request.EventCreationRequest;
import com.microsv.ai_service.dto.request.TaskCreationRequest;
import com.microsv.ai_service.dto.response.Mode3ChatResponse;
import com.microsv.ai_service.dto.response.TaskResponse;
import com.microsv.ai_service.enumeration.TaskStatus;
import com.microsv.ai_service.service.Mode3ChatService;
import com.microsv.ai_service.service.Mode3PromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@Slf4j
public class Mode3ChatServiceImpl implements Mode3ChatService {

    public static final String CONVERSATION_ID_PREFIX = "3_";
    private static final ZoneOffset UTC7 = ZoneOffset.ofHours(7);
    private static final DateTimeFormatter VI_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ChatClient chatClient;
    private final TaskClient taskClient;
    private final Mode3PromptService mode3PromptService;
    private final ObjectMapper objectMapper;

    public Mode3ChatServiceImpl(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            TaskClient taskClient,
            Mode3PromptService mode3PromptService,
            ObjectMapper objectMapper) {
        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.taskClient = taskClient;
        this.mode3PromptService = mode3PromptService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mode3ChatResponse chat(String message, String conversationId, Long userId) {
        if (message == null || message.trim().isEmpty()) {
            return Mode3ChatResponse.builder()
                    .structured(false)
                    .message("Bạn muốn tạo công việc hay sự kiện mới? Hãy cho tôi biết thêm thông tin nhé!")
                    .canApply(false)
                    .build();
        }

        try {
            String systemPrompt = mode3PromptService.buildSystemPrompt();

            String rawResponse = chatClient.prompt()
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .system(systemPrompt)
                    .user(message)
                    .call()
                    .content()
                    .trim();

            Mode3ChatResponse parsed = parseToMode3Response(rawResponse);

            // Nếu AI bảo đủ thông tin → gọi API tạo
            if (parsed.isCanApply() && parsed.getCreated() != null) {
                try {
                    TaskCreationRequest request = buildCreationRequest(parsed.getCreated());
                    TaskResponse created = taskClient.createTaskByAI(userId, request);

                    String successMsg = String.format(
                            "Đã tạo %s \"%s\" thành công! ⭐ Priority: %s, Bắt đầu: %s, Deadline: %s",
                            parsed.getCreated().getType(),
                            created.getTitle(),
                            created.getPriority(),
                            parsed.getCreated().getStartTime() != null ? parsed.getCreated().getStartTime() : "Không có",
                            created.getDeadline() != null ? created.getDeadline().toString() : "Không có"
                    );

                    return Mode3ChatResponse.builder()
                            .structured(true)
                            .message(successMsg)
                            .conversationId(conversationId)
                            .canApply(false)
                            .created(Mode3ChatResponse.CreatedItem.builder()
                                    .taskId(created.getTaskId())
                                    .type(parsed.getCreated().getType())
                                    .title(created.getTitle())
                                    .startTime(parsed.getCreated().getStartTime())
                                    .deadline(created.getDeadline() != null ? created.getDeadline().toString() : null)
                                    .priority(created.getPriority() != null ? created.getPriority().name() : null)
                                    .category(parsed.getCreated().getCategory())
                                    .status(created.getStatus() != null ? created.getStatus().name() : "TODO")
                                    .description(parsed.getCreated().getDescription())
                                    .reminderMinutesBefore(parsed.getCreated().getReminderMinutesBefore())
                                    .invitedEmails(parsed.getCreated().getInvitedEmails())
                                    .eventDescription(parsed.getCreated().getEventDescription())
                                    .isOnline(parsed.getCreated().getIsOnline())
                                    .linkEvent(parsed.getCreated().getLinkEvent())
                                    .location(parsed.getCreated().getLocation())
                                    .build())
                            .build();

                } catch (Exception createError) {
                    log.error("Failed to create task via AI for user {}: {}", userId, createError.getMessage(), createError);
                    return Mode3ChatResponse.builder()
                            .structured(false)
                            .message("Tôi đã trích xuất thông tin nhưng không thể tạo lúc này: " + createError.getMessage())
                            .canApply(false)
                            .build();
                }
            }

            parsed.setConversationId(conversationId);
            return parsed;

        } catch (Exception e) {
            log.error("Error in Mode3 chat for user {}: {}", userId, e.getMessage(), e);
            return Mode3ChatResponse.builder()
                    .structured(false)
                    .message("Xin lỗi, đã có lỗi xảy ra. Vui lòng thử lại.")
                    .canApply(false)
                    .build();
        }
    }

    /**
     * Parse AI JSON response → Mode3ChatResponse.
     * Handles: JSON, plain text (→ unstructured).
     */
    private Mode3ChatResponse parseToMode3Response(String raw) {
        String cleaned = raw.trim();
        if (!cleaned.startsWith("{")) {
            int brace = cleaned.indexOf('{');
            if (brace >= 0) cleaned = cleaned.substring(brace);
        }

        try {
            JsonNode root = objectMapper.readTree(cleaned);

            Mode3ChatResponse.CreatedItem created = null;
            if (root.has("created") && !root.get("created").isNull()) {
                JsonNode c = root.get("created");
                created = Mode3ChatResponse.CreatedItem.builder()
                        .type(safeText(c, "type"))
                        .title(safeText(c, "title"))
                        .startTime(safeText(c, "startTime"))
                        .deadline(safeText(c, "deadline"))
                        .priority(safeText(c, "priority"))
                        .category(safeText(c, "category"))
                        .status(safeText(c, "status"))
                        .description(safeText(c, "description"))
                        .reminderMinutesBefore(c.has("reminderMinutesBefore") && !c.get("reminderMinutesBefore").isNull() ? c.get("reminderMinutesBefore").asInt() : null)
                        .eventDescription(safeText(c, "eventDescription"))
                        .isOnline(c.has("isOnline") && !c.get("isOnline").isNull() ? c.get("isOnline").asBoolean() : null)
                        .linkEvent(safeText(c, "linkEvent"))
                        .location(safeText(c, "location"))
                        .build();

                if (c.has("invitedEmails") && c.get("invitedEmails").isArray()) {
                    List<String> emails = new java.util.ArrayList<>();
                    c.get("invitedEmails").forEach(node -> emails.add(node.asText()));
                    created.setInvitedEmails(emails);
                }
            }

            return Mode3ChatResponse.builder()
                    .structured(root.has("structured") ? root.get("structured").asBoolean() : true)
                    .message(safeText(root, "message"))
                    .canApply(root.has("canApply") ? root.get("canApply").asBoolean() : false)
                    .created(created)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse Mode3 JSON, returning as plain text: {}", e.getMessage());
            return Mode3ChatResponse.builder()
                    .structured(false)
                    .message(raw)
                    .canApply(false)
                    .build();
        }
    }

    /**
     * Build TaskCreationRequest from AI-extracted CreatedItem.
     * Convert dd/MM/yyyy HH:mm → OffsetDateTime (UTC+7).
     */
    private TaskCreationRequest buildCreationRequest(Mode3ChatResponse.CreatedItem item) {
        TaskCreationRequest request = new TaskCreationRequest();
        request.setTitle(item.getTitle());
        request.setStartTime(parseToOffsetDateTime(item.getStartTime()));
        request.setDeadline(parseToOffsetDateTime(item.getDeadline()));
        request.setDescription(item.getDescription());

        if (item.getStatus() != null) {
            try {
                request.setStatus(TaskStatus.valueOf(item.getStatus().toUpperCase().trim()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status '{}', defaulting to TODO", item.getStatus());
                request.setStatus(TaskStatus.TODO);
            }
        }

        if (item.getCategory() != null) {
            request.setDescription(
                    (item.getDescription() != null ? item.getDescription() + "\n" : "")
                            + "[Danh mục: " + item.getCategory() + "]"
            );
        }

        boolean isEvent = "sự kiện".equalsIgnoreCase(item.getType()) || "event".equalsIgnoreCase(item.getType());
        request.setIsEvent(isEvent);

        if (isEvent) {
            EventCreationRequest eventReq = new EventCreationRequest();
            eventReq.setEventDescription(item.getEventDescription());
            eventReq.setIsOnline(item.getIsOnline());
            eventReq.setLinkEvent(item.getLinkEvent());
            eventReq.setLocation(item.getLocation());
            eventReq.setStartTime(parseToOffsetDateTime(item.getStartTime()));
            eventReq.setReminderMinutesBefore(
                    item.getReminderMinutesBefore() != null ? item.getReminderMinutesBefore() : 30
            );
            eventReq.setInvitedEmails(item.getInvitedEmails());
            request.setEventCreationRequest(eventReq);
        }

        return request;
    }

    /**
     * Parse "dd/MM/yyyy HH:mm" → OffsetDateTime at UTC+7.
     */
    private OffsetDateTime parseToOffsetDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) return null;
        try {
            LocalDate date = LocalDate.parse(dateTimeStr.substring(0, 10),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            LocalTime time;
            if (dateTimeStr.contains(" ")) {
                time = LocalTime.parse(dateTimeStr.substring(11),
                        DateTimeFormatter.ofPattern("HH:mm"));
            } else {
                time = LocalTime.NOON;
            }
            return OffsetDateTime.of(date, time, UTC7);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse datetime: {}", dateTimeStr);
            return null;
        }
    }

    private String safeText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}
