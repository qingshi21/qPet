package com.lumenami.backend.service;

import com.lumenami.backend.dto.ChatRequest;
import com.lumenami.backend.dto.ChatResponse;
import com.lumenami.backend.model.ChatMessage;

import java.util.List;

/**
 * 聊天服务接口
 */
public interface ChatService {

    /**
     * 处理聊天请求
     * @param userId 用户ID
     * @param request 聊天请求
     * @return 聊天响应
     */
    ChatResponse chat(Integer userId, ChatRequest request);

    /**
     * 获取宠物的对话历史
     * @param userId 用户ID
     * @param petId 宠物ID
     * @return 对话历史列表
     */
    List<ChatMessage> getHistory(Integer userId, Integer petId);
}
