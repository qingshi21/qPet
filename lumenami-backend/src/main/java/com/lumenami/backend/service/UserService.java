package com.lumenami.backend.service;

import com.lumenami.backend.dto.LoginRequest;
import com.lumenami.backend.dto.LoginResponse;
import com.lumenami.backend.dto.RegisterRequest;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户注册
     * @param request 注册请求
     */
    void register(RegisterRequest request);

    /**
     * 用户登录
     * @param request 登录请求
     * @return 登录响应（包含 userId 和 username）
     */
    LoginResponse login(LoginRequest request);
}