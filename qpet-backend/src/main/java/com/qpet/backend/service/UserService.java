package com.qpet.backend.service;

import com.qpet.backend.dto.LoginRequest;
import com.qpet.backend.dto.LoginResponse;
import com.qpet.backend.dto.RegisterRequest;
import com.qpet.backend.exception.BusinessException;
import com.qpet.backend.mapper.UserMapper;
import com.qpet.backend.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public void register(RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new BusinessException(400, "用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new BusinessException(400, "密码不能少于6位");
        }
        if (userMapper.findByUsername(request.getUsername()) != null) {
            throw new BusinessException(400, "用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        userMapper.insert(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.findByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!user.getPassword().equals(request.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        LoginResponse resp = new LoginResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        return resp;
    }
}