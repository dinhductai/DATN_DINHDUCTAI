package com.microsv.ai_service.service.impl;

import com.microsv.ai_service.client.TaskClient;
import com.microsv.ai_service.dto.response.TaskResponse;
import com.microsv.ai_service.entity.ConversationMemory;
import com.microsv.ai_service.repository.ConversationMemoryRepository;
import com.microsv.ai_service.service.ConversationMemoryService;
import com.microsv.ai_service.util.NullUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMemoryServiceImpl implements ChatMemory , ConversationMemoryService {
    private final ConversationMemoryRepository conversationMemoryRepository;
    private final TaskClient taskClient;

    @Override
    public void add(String conversationId, List<Message> messages) {
        Long currentUserId = getCurrentUserId();
        NullUtil.checkUserNullByUserId(currentUserId);

        for (Message message : messages) {
            ConversationMemory cvMemory = ConversationMemory.builder()
                    .conversationId(conversationId)
                    .role(message.getMessageType().getValue().toUpperCase())
                    .content(message.getText())
                    .userId(currentUserId)
                    .build();
            conversationMemoryRepository.save(cvMemory);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        NullUtil.checkUserNullByUserId(getCurrentUserId());
        List<ConversationMemory> cvMemory = conversationMemoryRepository.findByConversationId(conversationId);

        // Lấy tasks đã lọc - chỉ TODO/IN_PROGRESS, trong 7 ngày tới, limit 20
        List<TaskResponse> tasks = getSmartFilteredTasks(getCurrentUserId());

        Message taskContext = createTaskContext(tasks);
        List<Message> messages = new ArrayList<>();
        if(!tasks.isEmpty()){
            messages.add(taskContext);
        }
        messages.addAll(cvMemory.stream().map(this::convertToMessage).collect(Collectors.toList()));
        return messages;
    }

    /**
     * Lấy tasks thông minh cho AI context
     * - Chỉ lấy TODO và IN_PROGRESS (không lấy DONE)
     * - Chỉ lấy trong 7 ngày tới
     * - Limit 20 tasks để tránh context quá dài
     */
    private List<TaskResponse> getSmartFilteredTasks(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate nextWeek = today.plusDays(7);

        try {
            return taskClient.getFilteredTasks(
                    userId,
                    null, // không lọc theo status - lấy cả TODO và IN_PROGRESS
                    null, // không lọc theo priority
                    today,
                    nextWeek,
                    20   // limit 20 tasks
            );
        } catch (Exception e) {
            // Fallback: thử lấy tất cả nếu filter fails
            try {
                return taskClient.getUserTasks(userId);
            } catch (Exception ex) {
                return List.of();
            }
        }
    }

    @Override
    public void clear(String conversationId) {
        Long currentUserId = getCurrentUserId();
        NullUtil.checkUserNullByUserId(currentUserId);
        conversationMemoryRepository.deleteByConversationIdAndUserId(conversationId, currentUserId);
    }

    private Message createTaskContext(List<TaskResponse> taskResponses) {
        StringBuilder context = new StringBuilder();
        context.append("═══════════════════════════════════════════════════\n");
        context.append("📋 DỮ LIỆU TASK CỦA USER (7 NGÀY TỚI)\n");
        context.append("═══════════════════════════════════════════════════\n\n");

        if (taskResponses.isEmpty()) {
            context.append("⚠️ KHÔNG CÓ TASK NÀO TRONG 7 NGÀY TỚI\n");
        }
        else{
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            // Đếm stats
            long todoCount = taskResponses.stream().filter(t -> "TODO".equals(t.getStatus().name())).count();
            long inProgressCount = taskResponses.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus().name())).count();
            long highPriority = taskResponses.stream().filter(t -> "HIGH".equals(t.getPriority().name())).count();

            context.append(String.format("📊 Tổng quan: %d tasks | TODO: %d | Đang làm: %d | HIGH priority: %d\n\n",
                    taskResponses.size(), todoCount, inProgressCount, highPriority));

            for(TaskResponse taskResponse : taskResponses){
                String emoji = getPriorityEmoji(taskResponse.getPriority().name());
                String statusIcon = getStatusIcon(taskResponse.getStatus().name());

                context.append(String.format("%s %s %s\n", emoji, statusIcon, taskResponse.getTitle()));
                context.append(String.format("   📅 Deadline: %s | ⭐ Priority: %s | 📌 Status: %s\n",
                        taskResponse.getDeadline() != null ? taskResponse.getDeadline().format(formatter) : "Không có",
                        taskResponse.getPriority().name(),
                        taskResponse.getStatus().name()));
                if (taskResponse.getDescription() != null && !taskResponse.getDescription().isBlank()) {
                    context.append(String.format("   📝 Mô tả: %s\n", truncate(taskResponse.getDescription(), 100)));
                }
                context.append("\n");
            }
        }

        context.append("═══════════════════════════════════════════════════\n");
        context.append("Dựa vào dữ liệu TRÊN để trả lời câu hỏi của user.\n");
        context.append("Nếu user hỏi về task cụ thể - tra cứu trong danh sách trên.\n");
        context.append("Nếu user hỏi sắp xếp - xếp theo deadline và priority.\n");
        context.append("═══════════════════════════════════════════════════\n");

        return new SystemMessage(context.toString());
    }

    private String getPriorityEmoji(String priority) {
        if (priority == null) return "⚪";
        return switch (priority.toUpperCase()) {
            case "HIGH" -> "🔴";
            case "MEDIUM" -> "🟡";
            case "LOW" -> "🟢";
            default -> "⚪";
        };
    }

    private String getStatusIcon(String status) {
        if (status == null) return "❓";
        return switch (status.toUpperCase()) {
            case "TODO" -> "📝";
            case "IN_PROGRESS" -> "🔄";
            case "DONE" -> "✅";
            default -> "❓";
        };
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            return Long.parseLong(jwt.getSubject());
        }
        return null;
    }

    private Message convertToMessage(ConversationMemory memory){
        if("USER".equalsIgnoreCase(memory.getRole())){
            return new UserMessage(memory.getContent());
        }
        else {
            return new AssistantMessage(memory.getContent());
        }
    }

    @Override
    public String getConversationId(Long userId) {
        ConversationMemory conversationMemory = conversationMemoryRepository.findFirstByUserId(userId).orElseThrow();
        return conversationMemory.getConversationId();
    }

    @Override
    public Page<ConversationMemory> getConversationMemory(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page,size);
        return conversationMemoryRepository.findAllByUserId(userId,pageable);
    }
}