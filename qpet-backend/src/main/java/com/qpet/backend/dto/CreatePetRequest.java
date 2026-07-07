package com.qpet.backend.dto;

import lombok.Data;

@Data
public class CreatePetRequest {
    private String name;
    private String roleName;
    private String systemPrompt;
}