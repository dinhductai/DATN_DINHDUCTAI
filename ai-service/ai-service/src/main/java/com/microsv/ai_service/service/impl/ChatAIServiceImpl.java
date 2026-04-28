package com.microsv.ai_service.service.impl;

import com.microsv.ai_service.client.TaskClient;
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

@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatAIServiceImpl implements ChatAIService {
    ChatClient chatClient;
    TaskClient taskClient;

    public ChatAIServiceImpl(ChatClient.Builder builder, ChatMemory chatMemory, TaskClient taskClient) {
        this.chatClient = builder
                .defaultSystem(PromptUtil.SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.taskClient = taskClient;
    }

    @Override
    public String chat(String message, MultipartFile file, String conversationId, Long userId) {
        if (message == null || message.trim().isEmpty()) {
            message = "Xin chào";
        }

        try {
            // Lấy task JSON đã được Claude convert từ Redis
            String taskJson = getTaskJsonFromRedis(userId);
            
            // Enhance system prompt với task data JSON
            String enhancedPrompt = PromptUtil.SYSTEM_PROMPT + 
                "\n\n" + "DỮ LIỆU TASK HIỆN TẠI (JSON):\n" + taskJson;

            // Gọi OpenAI với prompt đã enhance
            return chatClient.prompt()
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .system(enhancedPrompt)
                    .user(message)
                    .call()
                    .content().trim();

        } catch (Exception e) {
            log.error("Error in chat: {}", e.getMessage());
            // Fallback: chat không có context
            return chatClient.prompt()
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .user(message)
                    .call()
                    .content().trim();
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
}
