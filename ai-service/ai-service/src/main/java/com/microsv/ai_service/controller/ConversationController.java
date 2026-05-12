package com.microsv.ai_service.controller;

import com.microsv.ai_service.dto.response.AIRichResponse;
import com.microsv.ai_service.dto.response.ChatAIConversationResponse;
import com.microsv.ai_service.dto.response.Mode1ChatResponse;
import com.microsv.ai_service.dto.response.Mode2ChatResponse;
import com.microsv.ai_service.entity.ConversationMemory;
import com.microsv.ai_service.service.impl.ChatAIServiceImpl;
import com.microsv.ai_service.service.impl.ChatMemoryServiceImpl;
import com.microsv.ai_service.service.impl.Mode1ChatServiceImpl;
import com.microsv.ai_service.service.impl.Mode2ChatServiceImpl;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/ai")
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class ConversationController {
    ChatMemoryServiceImpl chatMemoryService;
    ChatAIServiceImpl chatAIService;
    Mode1ChatServiceImpl mode1ChatService;
    Mode2ChatServiceImpl mode2ChatService;

    /**
     * Mode 1 chat — thống kê & hỏi đáp về lịch trình.
     * conversationId format: "1_<uuid>" — mode + underscore + uuid.
     * Nếu conversationId rỗng → tạo mới "1_<uuid>".
     */
    @PostMapping("/mode/1")
    public ResponseEntity<Mode1ChatResponse> chatMode1(
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @RequestParam(value = "mode", defaultValue = "1") Integer mode,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());

        if (conversationId == null || conversationId.isBlank()) {
            conversationId = Mode1ChatServiceImpl.CONVERSATION_ID_PREFIX + UUID.randomUUID().toString();
        }

        Mode1ChatResponse response = mode1ChatService.chat(message, conversationId, userId);
        response.setConversationId(conversationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Mode 2 chat — tư vấn sắp xếp lịch trình.
     * conversationId format: "2_<uuid>" — mode + underscore + uuid.
     * Nếu user nhắn "chỉnh sửa lại" → gọi Claude API để cập nhật task/event.
     */
    @PostMapping("/mode/2")
    public ResponseEntity<Mode2ChatResponse> chatMode2(
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @RequestParam(value = "mode", defaultValue = "2") Integer mode,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());

        if (conversationId == null || conversationId.isBlank()) {
            conversationId = Mode2ChatServiceImpl.CONVERSATION_ID_PREFIX + UUID.randomUUID().toString();
        }

        Mode2ChatResponse response = mode2ChatService.chat(message, conversationId, userId);
        response.setConversationId(conversationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ChatAIConversationResponse> chat(
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId  = Long.parseLong(jwt.getSubject());
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }
        String chatAIResponses = chatAIService.chat(message, file, conversationId, userId);
        ChatAIConversationResponse response = new ChatAIConversationResponse(conversationId, chatAIResponses);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rich")
    public ResponseEntity<AIRichResponse> chatRich(
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId  = Long.parseLong(jwt.getSubject());
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }
        AIRichResponse response = chatAIService.chatRich(message, file, conversationId, userId);
        response.setConversationId(conversationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/id")
    public ResponseEntity<String> getConversationId(
            @RequestParam(value = "mode", defaultValue = "1") Integer mode,
            @AuthenticationPrincipal Jwt jwt){
        Long userId  = Long.parseLong(jwt.getSubject());
        String conversationId = chatMemoryService.getConversationId(userId, mode);
        return ResponseEntity.ok(conversationId);
    }

    @GetMapping
    public ResponseEntity<Page<ConversationMemory>> getConversationMemory(@AuthenticationPrincipal Jwt jwt,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "10") int size){
        Long userId  = Long.parseLong(jwt.getSubject());
        Page<ConversationMemory> conversation = chatMemoryService.getConversationMemory(userId, page, size);
        return ResponseEntity.ok(conversation);
    }

    @GetMapping("/history")
    public ResponseEntity<Page<ConversationMemory>> getConversationHistory(
            @RequestParam(value = "conversationId") String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        Page<ConversationMemory> history = chatMemoryService.getConversationHistory(conversationId, page, size, userId);
        return ResponseEntity.ok(history);
    }
}
