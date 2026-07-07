package com.qpet.backend.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private Integer userId;
    private String username;
}