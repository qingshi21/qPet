package com.lumenami.backend.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
    private Integer id;
    private String username;
    private String password;          // 存加密后的密码
    private LocalDateTime createdAt;
}