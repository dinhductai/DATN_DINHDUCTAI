package com.microsv.ai_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsv.ai_service.client.TaskClient;
import com.microsv.ai_service.dto.response.AIRichResponse;
import com.microsv.ai_service.dto.response.AIRichResponse.RecommendationItem;
import com.microsv.ai_service.dto.response.AIRichResponse.SummaryData;
import com.microsv.ai_service.dto.response.AIRichResponse.TaskItem;
import com.microsv.ai_service.service.ChatAIService;
import com.microsv.ai_service.util.PromptUtil;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatAIServiceImpl implements ChatAIService {
    ChatClient chatClient;
    TaskClient taskClient;
    ObjectMapper objectMapper;

    public ChatAIServiceImpl(ChatClient.Builder builder, ChatMemory chatMemory, TaskClient taskClient, ObjectMapper objectMapper) {
        this.chatClient = builder
                .defaultSystem(PromptUtil.SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.taskClient = taskClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String chat(String message, MultipartFile file, String conversationId, Long userId) {
        if (message == null || message.trim().isEmpty()) {
            message = "Xin chào";
        }

        try {
            String taskJson = getTaskJsonFromRedis(userId);
            String currentDateInfo = getCurrentDateInfo();
            String enhancedPrompt = PromptUtil.SYSTEM_PROMPT +
                "\n\n" + currentDateInfo +
                "\n\n" + "DỮ LIỆU TASK HIỆN TẠI (JSON):\n" + taskJson;

            return chatClient.prompt()
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .system(enhancedPrompt)
                    .user(message)
                    .call()
                    .content().trim();

        } catch (Exception e) {
            log.error("Error in chat: {}", e.getMessage());
            return chatClient.prompt()
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .user(message)
                    .call()
                    .content().trim();
        }
    }

    @Override
    public AIRichResponse chatRich(String message, MultipartFile file, String conversationId, Long userId) {
        if (message == null || message.trim().isEmpty()) {
            message = "Xin chào";
        }

        try {
            String taskJson = getTaskJsonFromRedis(userId);
            String currentDateInfo = getCurrentDateInfo();
            String enhancedPrompt = PromptUtil.SYSTEM_PROMPT +
                "\n\n" + currentDateInfo +
                "\n\n" + "DỮ LIỆU TASK HIỆN TẠI (JSON):\n" + taskJson;

            String rawResponse = chatClient.prompt()
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .system(enhancedPrompt)
                    .user(message)
                    .call()
                    .content().trim();

            return parseToRichResponse(rawResponse);

        } catch (Exception e) {
            log.error("Error in chatRich: {}", e.getMessage());
            return AIRichResponse.builder()
                    .structured(false)
                    .message("Xin lỗi, đã có lỗi xảy ra. Vui lòng thử lại.")
                    .build();
        }
    }

    private AIRichResponse parseToRichResponse(String rawJson) {
        String cleanedJson = rawJson.trim();

        if (!cleanedJson.startsWith("{")) {
            int firstBrace = cleanedJson.indexOf('{');
            if (firstBrace >= 0) {
                cleanedJson = cleanedJson.substring(firstBrace);
            }
        }

        try {
            JsonNode root = objectMapper.readTree(cleanedJson);

            SummaryData summary = null;
            if (root.has("summary") && !root.get("summary").isNull()) {
                JsonNode s = root.get("summary");
                summary = SummaryData.builder()
                        .totalTasks(s.has("totalTasks") && !s.get("totalTasks").isNull() ? s.get("totalTasks").asInt() : 0)
                        .pendingTasks(s.has("pendingTasks") && !s.get("pendingTasks").isNull() ? s.get("pendingTasks").asInt() : 0)
                        .overdueTasks(s.has("overdueTasks") && !s.get("overdueTasks").isNull() ? s.get("overdueTasks").asInt() : 0)
                        .completedToday(s.has("completedToday") && !s.get("completedToday").isNull() ? s.get("completedToday").asInt() : 0)
                        .completionRate(s.has("completionRate") && !s.get("completionRate").isNull() ? s.get("completionRate").asDouble() : 0.0)
                        .build();
            }

            List<TaskItem> taskItems = new ArrayList<>();
            if (root.has("tasks") && root.get("tasks").isArray()) {
                for (JsonNode t : root.get("tasks")) {
                    taskItems.add(TaskItem.builder()
                            .emoji(t.has("emoji") && !t.get("emoji").isNull() ? t.get("emoji").asText() : "")
                            .taskId(t.has("taskId") && !t.get("taskId").isNull() ? t.get("taskId").asLong() : null)
                            .title(t.has("title") && !t.get("title").isNull() ? t.get("title").asText() : "")
                            .description(t.has("description") && !t.get("description").isNull() ? t.get("description").asText() : null)
                            .deadline(t.has("deadline") && !t.get("deadline").isNull() ? t.get("deadline").asText() : null)
                            .priority(t.has("priority") && !t.get("priority").isNull() ? t.get("priority").asText() : null)
                            .status(t.has("status") && !t.get("status").isNull() ? t.get("status").asText() : null)
                            .reason(t.has("reason") && !t.get("reason").isNull() ? t.get("reason").asText() : null)
                            .build());
                }
            }

            List<RecommendationItem> recommendations = new ArrayList<>();
            if (root.has("recommendations") && root.get("recommendations").isArray()) {
                for (JsonNode r : root.get("recommendations")) {
                    recommendations.add(RecommendationItem.builder()
                            .taskId(r.has("taskId") && !r.get("taskId").isNull() ? r.get("taskId").asLong() : null)
                            .taskTitle(r.has("taskTitle") && !r.get("taskTitle").isNull() ? r.get("taskTitle").asText() : "")
                            .reason(r.has("reason") && !r.get("reason").isNull() ? r.get("reason").asText() : null)
                            .order(r.has("order") && !r.get("order").isNull() ? r.get("order").asInt() : 0)
                            .build());
                }
            }

            return AIRichResponse.builder()
                    .structured(root.has("structured") ? root.get("structured").asBoolean() : true)
                    .message(root.has("message") && !root.get("message").isNull() ? root.get("message").asText() : "")
                    .summary(summary)
                    .tasks(taskItems)
                    .recommendations(recommendations)
                    .motivation(root.has("motivation") && !root.get("motivation").isNull() ? root.get("motivation").asText() : null)
                    .followUp(root.has("followUp") && !root.get("followUp").isNull() ? root.get("followUp").asText() : null)
                    .build();

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse AI JSON response, returning as plain text: {}", e.getMessage());
            return AIRichResponse.builder()
                    .structured(false)
                    .message(rawJson)
                    .build();
        }
    }

    private String getTaskJsonFromRedis(Long userId) {
        try {
            String json = taskClient.getTasksJsonForAI(userId);
            return json != null && !json.isEmpty() ? json : "{\"tasks\": []}";
        } catch (Exception e) {
            log.warn("Failed to get task JSON from Redis for user {}: {}", userId, e.getMessage());
            return "{\"tasks\": []}";
        }
    }

    /**
     * Trả về thông tin ngày hiện tại theo múi giờ Việt Nam (Asia/Ho_Chi_Minh).
     * Rất quan trọng: AI phải biết HÔM NAY là ngày nào để so sánh deadline chính xác.
     */
    private String getCurrentDateInfo() {
        ZonedDateTime nowVietnam = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        // Thông tin cơ bản
        String formatted = nowVietnam.format(
            DateTimeFormatter.ofPattern("EEEE', ngày' dd' tháng' MM' năm' yyyy", new java.util.Locale("vi", "VN"))
        );
        String currentTime = nowVietnam.format(DateTimeFormatter.ofPattern("HH:mm"));
        int currentHour = nowVietnam.getHour();

        // Tính tuần hiện tại
        ZonedDateTime startOfWeek = nowVietnam.minusDays(nowVietnam.getDayOfWeek().getValue() - 1)
            .toLocalDate().atStartOfDay(nowVietnam.getZone());
        ZonedDateTime endOfWeek = startOfWeek.plusDays(6);

        // Tính tháng trước
        ZonedDateTime lastMonth = nowVietnam.minusMonths(1);
        ZonedDateTime startOfLastMonth = lastMonth.withDayOfMonth(1);
        ZonedDateTime endOfLastMonth = lastMonth.withDayOfMonth(lastMonth.toLocalDate().lengthOfMonth());

        // Tính tuần trước
        ZonedDateTime lastWeekStart = startOfWeek.minusWeeks(1);
        ZonedDateTime lastWeekEnd = startOfWeek.minusDays(1);

        // Chiều/Sáng/Tối
        String timeOfDay;
        if (currentHour < 12) {
            timeOfDay = "SÁNG";
        } else if (currentHour < 18) {
            timeOfDay = "CHIỀU";
        } else {
            timeOfDay = "TỐI";
        }

        // Định dạng ngày theo kiểu số cho AI dễ parse
        String today = nowVietnam.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String tomorrow = nowVietnam.plusDays(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String yesterday = nowVietnam.minusDays(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String startOfWeekStr = startOfWeek.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String endOfWeekStr = endOfWeek.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String lastWeekStartStr = lastWeekStart.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String lastWeekEndStr = lastWeekEnd.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String lastMonthStartStr = startOfLastMonth.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String lastMonthEndStr = endOfLastMonth.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        return """
            ═══════════════════════════════════════════════════
            THÔNG TIN THỜI GIAN HIỆN TẠI (RẤT QUAN TRỌNG)
            ═══════════════════════════════════════════════════
            HÔM NAY = %s (%s)
            Giờ hiện tại: %s

            Tuần này: %s → %s
            Tuần trước: %s → %s
            Tháng trước: %s → %s
            ═══════════════════════════════════════════════════

            QUY TẮC SO SÁNH (BẮT BUỘC):
            - "Hôm nay" = %s (ví dụ: 29/04/2026)
            - "Ngày mai" = %s (ví dụ: 30/04/2026) ← KHÔNG PHẢI %s!
            - "Hôm qua" = %s
            - "Chiều nay" = %s, 12:00-18:00
            - "Sáng nay" = %s, 00:00-12:00 (hiện tại: %s)
            - "Trong tuần này" = %s → %s
            - "Tuần trước" = %s → %s (không có task → [])
            - "Tháng trước" = %s → %s (không có task → [])
            - "Vào lúc XhY" = tìm task deadline gần nhất với XhY
            ═══════════════════════════════════════════════════
            """.formatted(
                formatted, today, currentTime,
                startOfWeekStr, endOfWeekStr,
                lastWeekStartStr, lastWeekEndStr,
                lastMonthStartStr, lastMonthEndStr,
                today,
                tomorrow, today,    // ngày mai ≠ hôm nay
                yesterday,
                today, today, currentTime,
                startOfWeekStr, endOfWeekStr,
                lastWeekStartStr, lastWeekEndStr,
                lastMonthStartStr, lastMonthEndStr
            );
    }
}
