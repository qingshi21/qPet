package com.lumenami.backend.controller;

import com.lumenami.backend.dto.LoginRequest;
import com.lumenami.backend.dto.LoginResponse;
import com.lumenami.backend.dto.RegisterRequest;
import com.lumenami.backend.dto.Result;
import com.lumenami.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<String> register(@RequestBody RegisterRequest request) {
        userService.register(request);
        return Result.success("注册成功");
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse data = userService.login(request);
        return Result.success(data);
    }
}