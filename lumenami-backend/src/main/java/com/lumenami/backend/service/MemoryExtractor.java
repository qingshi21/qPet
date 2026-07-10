package com.lumenami.backend.service;

import java.util.List;
import java.util.Map;

/**
 * 记忆提取服务接口
 */
public interface MemoryExtractor {

    /**
     * 从对话中提炼记忆
     * @param userMessage 用户消息
     * @param aiResponse AI回复
     * @return 提取的记忆列表，如果没有需要存储的记忆则返回空列表
     */
    List<Map<String, Object>> extractMemories(String userMessage, String aiResponse);
}
