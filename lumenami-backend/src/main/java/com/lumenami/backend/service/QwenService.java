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

    /**
     * 调用 Qwen Embedding API 生成文本向量
     * @param text 输入文本
     * @return embedding 向量（JSON 字符串格式）
     */
    String embedding(String text);

    /**
     * 调用 Qwen API 对多条记忆值进行总结
     * @param key 记忆键名
     * @param values 多个版本的记忆值
     * @return 总结后的记忆值
     */
    String summarize(String key, List<String> values);
}
