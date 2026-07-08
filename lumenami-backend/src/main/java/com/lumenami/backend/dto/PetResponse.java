package com.lumenami.backend.dto;

import lombok.Data;

@Data
public class PetResponse {
    private Integer petId;
    private String name;
    private String roleName;
    private Boolean isActive;
}