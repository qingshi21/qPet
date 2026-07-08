package com.lumenami.backend.dto;

import lombok.Data;

@Data
public class CreatePetRequest {
    private String name;
    private String roleName;
    private String systemPrompt;
}