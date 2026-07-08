package com.lumenami.backend.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Pet {
    private Integer id;
    private Integer userId;
    private String name;
    private String roleName;
    private String systemPrompt;
    private Integer isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}