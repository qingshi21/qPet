package com.lumenami.backend.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
    private Boolean rememberMe; // 是否记住登录（7天免密）
}