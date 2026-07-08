package com.lumenami.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {
    private Integer petId;
    private String message;
    private List<Message> history;

    @Data
    public static class Message {
        private String role;    // "user" or "assistant"
        private String content;
    }
}
