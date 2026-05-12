package com.microsv.ai_service.service;

import com.microsv.ai_service.dto.response.Mode2ChatResponse;

public interface Mode2ChatService {
    Mode2ChatResponse chat(String message, String conversationId, Long userId);
}
