package com.lumenami.backend.controller;

import com.lumenami.backend.dto.ChatRequest;
import com.lumenami.backend.dto.ChatResponse;
import com.lumenami.backend.dto.Result;
import com.lumenami.backend.model.ChatMessage;
import com.lumenami.backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public Result<ChatResponse> chat(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody ChatRequest request) {
        ChatResponse response = chatService.chat(userId, request);
        return Result.success(response);
    }

    @GetMapping("/history/{petId}")
    public Result<List<ChatMessage>> getHistory(
            @RequestHeader("X-User-Id") Integer userId,
            @PathVariable Integer petId) {
        List<ChatMessage> history = chatService.getHistory(userId, petId);
        return Result.success(history);
    }
}
