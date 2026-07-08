package com.lumenami.backend.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatMessage {
    private Integer id;
    private Integer petId;
    private Integer userId;
    private String role;        // "user" or "assistant"
    private String content;
    private LocalDateTime createdAt;
}
