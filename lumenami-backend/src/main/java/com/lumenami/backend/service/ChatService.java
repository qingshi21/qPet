package com.lumenami.backend.service;

import com.lumenami.backend.dto.ChatRequest;
import com.lumenami.backend.dto.ChatResponse;
import com.lumenami.backend.model.ChatMessage;

import java.util.List;
import java.util.function.Consumer;

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
     * 流式处理聊天请求
     * @param userId 用户ID
     * @param request 聊天请求
     * @param onToken 每收到一个 token 时的回调
     */
    void chatStream(Integer userId, ChatRequest request, Consumer<String> onToken);

    /**
     * 获取宠物的对话历史
     * @param userId 用户ID
     * @param petId 宠物ID
     * @return 对话历史列表
     */
    List<ChatMessage> getHistory(Integer userId, Integer petId);
}
