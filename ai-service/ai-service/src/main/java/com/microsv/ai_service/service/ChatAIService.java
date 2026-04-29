package com.microsv.ai_service.service;

import com.microsv.ai_service.dto.response.AIRichResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ChatAIService {
    String chat(String message, MultipartFile file, String conversationId, Long userId);

    AIRichResponse chatRich(String message, MultipartFile file, String conversationId, Long userId);
}
