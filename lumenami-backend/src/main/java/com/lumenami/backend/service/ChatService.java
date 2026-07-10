package com.lumenami.backend.service;

import com.lumenami.backend.dto.ChatRequest;
import com.lumenami.backend.dto.ChatResponse;
import com.lumenami.backend.exception.BusinessException;
import com.lumenami.backend.mapper.ChatMessageMapper;
import com.lumenami.backend.mapper.PetMapper;
import com.lumenami.backend.model.ChatMessage;
import com.lumenami.backend.model.Pet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 异步提取并保存记忆（不阻塞主流程）
     * @param petId 宠物ID
     * @param userMessage 用户消息
     * @param aiResponse AI回复
     */
    void extractAndSaveMemoriesAsync(Integer petId, String userMessage, String aiResponse);
}
