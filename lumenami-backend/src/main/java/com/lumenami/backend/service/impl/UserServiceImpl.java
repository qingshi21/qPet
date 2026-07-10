package com.lumenami.backend.service.impl;

import com.lumenami.backend.dto.LoginRequest;
import com.lumenami.backend.dto.LoginResponse;
import com.lumenami.backend.dto.RegisterRequest;
import com.lumenami.backend.exception.BusinessException;
import com.lumenami.backend.mapper.UserMapper;
import com.lumenami.backend.model.User;
import com.lumenami.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public void register(RegisterRequest request) {
        log.info("用户注册请求: username={}", request.getUsername());
        
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new BusinessException(400, "用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new BusinessException(400, "密码不能少于6位");
        }
        if (userMapper.findByUsername(request.getUsername()) != null) {
            log.warn("用户注册失败，用户名已存在: username={}", request.getUsername());
            throw new BusinessException(400, "用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        userMapper.insert(user);
        
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("用户登录请求: username={}", request.getUsername());
        
        User user = userMapper.findByUsername(request.getUsername());
        if (user == null) {
            log.warn("用户登录失败，用户不存在: username={}", request.getUsername());
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!user.getPassword().equals(request.getPassword())) {
            log.warn("用户登录失败，密码错误: username={}", request.getUsername());
            throw new BusinessException(401, "用户名或密码错误");
        }

        LoginResponse resp = new LoginResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        
        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());
        return resp;
    }
}
