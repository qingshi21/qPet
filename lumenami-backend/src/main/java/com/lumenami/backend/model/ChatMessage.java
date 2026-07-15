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
    private Integer tokenCount; // 本次消息消耗的token数（用于统计）
    private String embedding;   // Qwen embedding 向量（JSON字符串，异步生成）
    private LocalDateTime createdAt;
}
