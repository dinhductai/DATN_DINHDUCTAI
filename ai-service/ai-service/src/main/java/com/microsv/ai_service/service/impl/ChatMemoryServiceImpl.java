package com.microsv.ai_service.service.impl;

import com.microsv.ai_service.client.TaskClient;
import com.microsv.ai_service.dto.response.TaskResponse;
import com.microsv.ai_service.entity.ConversationMemory;
import com.microsv.ai_service.repository.ConversationMemoryRepository;
import com.microsv.ai_service.service.ConversationMemoryService;
import com.microsv.ai_service.util.NullUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMemoryServiceImpl implements ChatMemory , ConversationMemoryService {
    private final ConversationMemoryRepository conversationMemoryRepository;
    private final TaskClient taskClient;
    private final org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    public static final String MODE1_PREFIX = "1_";
    public static final String MODE2_PREFIX = "2_";
    public static final String MODE3_PREFIX = "3_";
    public static final String MODE4_PREFIX = "4_";

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
        try {
            Long currentUserId = getCurrentUserId();
            NullUtil.checkUserNullByUserId(currentUserId);

            // Lấy tối đa 10 tin nhắn gần nhất của cuộc trò chuyện này
            List<ConversationMemory> cvMemory =
                    conversationMemoryRepository.findTop5ByConversationIdOrderByCreateAtDesc(conversationId);
            // Đảo ngược để có thứ tự cũ → mới (chat memory Advisor cần đúng thứ tự)
            List<ConversationMemory> orderedMemory = cvMemory.reversed();

            // Lấy tasks đã lọc - chỉ TODO/IN_PROGRESS, trong 7 ngày tới, limit 20
            List<TaskResponse> tasks = getSmartFilteredTasks(currentUserId);

            Message taskContext = createTaskContext(tasks);
            List<Message> messages = new ArrayList<>();
            if(!tasks.isEmpty()){
                messages.add(taskContext);
            }
            messages.addAll(orderedMemory.stream().map(this::convertToMessage).collect(Collectors.toList()));
            return messages;
        } catch (Exception e) {
            log.warn("Failed to load chat memory for conversation {}: {}", conversationId, e.getMessage());
            return List.of();
        }
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
        // Ưu tiên RequestContextHolder vì nó tồn tại suốt request lifecycle
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    Jwt jwt = jwtDecoder.decode(token);
                    return Long.parseLong(jwt.getSubject());
                } catch (Exception e) {
                    log.warn("Failed to decode JWT in ChatMemory: {}", e.getMessage());
                }
            }
        }

        // Fallback: SecurityContextHolder (hoạt động trong main controller thread)
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
        return getConversationId(userId, 1);
    }

    /**
     * Lấy conversationId mới nhất cho user theo mode.
     * Trả về format: "<mode>_<uuid>" (VD: "1_abc123...")
     * Nếu chưa có cuộc trò chuyện nào cho mode này → trả về null để FE tạo mới.
     */
    public String getConversationId(Long userId, Integer mode) {
        String prefix = getPrefixByMode(mode);
        List<ConversationMemory> memories = conversationMemoryRepository.findByConversationIdLike(prefix + "%");
        if (memories == null || memories.isEmpty()) {
            return null;
        }
        return memories.get(0).getConversationId();
    }

    /**
     * Lấy lịch sử chat theo conversationId cụ thể (có userId check).
     */
    public Page<ConversationMemory> getConversationHistory(String conversationId, int page, int size, Long userId) {
        Pageable pageable = PageRequest.of(page, size);
        return conversationMemoryRepository.findByConversationIdAndUserId(conversationId, userId, pageable);
    }

    private String getPrefixByMode(Integer mode) {
        return switch (mode) {
            case 1 -> MODE1_PREFIX;
            case 2 -> MODE2_PREFIX;
            case 3 -> MODE3_PREFIX;
            case 4 -> MODE4_PREFIX;
            default -> MODE1_PREFIX;
        };
    }

    @Override
    public Page<ConversationMemory> getConversationMemory(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page,size);
        return conversationMemoryRepository.findAllByUserId(userId,pageable);
    }
}