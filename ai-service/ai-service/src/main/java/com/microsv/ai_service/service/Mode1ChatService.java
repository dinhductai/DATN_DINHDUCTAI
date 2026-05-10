package com.microsv.ai_service.service;

import com.microsv.ai_service.dto.response.Mode1ChatResponse;

public interface Mode1ChatService {
    Mode1ChatResponse chat(String message, String conversationId, Long userId);
}
