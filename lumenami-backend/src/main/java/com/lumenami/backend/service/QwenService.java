package com.lumenami.backend.service;

import java.util.List;
import java.util.Map;

/**
 * Qwen AI 服务接口
 */
public interface QwenService {

    /**
     * 调用 Qwen API 进行对话
     * @param systemPrompt 宠物的性格设定
     * @param history 对话历史
     * @return AI 回复内容
     */
    String chat(String systemPrompt, List<Map<String, String>> history);
}
