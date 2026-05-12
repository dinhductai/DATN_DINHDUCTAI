package com.microsv.ai_service.service;

import com.microsv.ai_service.dto.response.Mode3ChatResponse;

public interface Mode3ChatService {
    Mode3ChatResponse chat(String message, String conversationId, Long userId);
}
