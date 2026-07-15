package com.lumenami.backend.service.impl;

import com.lumenami.backend.mapper.ChatMessageMapper;
import com.lumenami.backend.service.MemoryExtractor;
import com.lumenami.backend.service.MemoryService;
import com.lumenami.backend.service.QwenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 聊天异步操作服务
 * 独立为单独的服务类，避免 @Async 同类自调用失效的问题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAsyncService {

    private final ChatMessageMapper chatMessageMapper;
    private final QwenService qwenService;
    private final MemoryExtractor memoryExtractor;
    private final MemoryService memoryService;

    /**
     * 异步生成消息的 embedding
     */
    @Async
    public void generateEmbeddingAsync(Integer messageId, String content) {
        try {
            String embedding = qwenService.embedding(content);
            if (embedding != null) {
                chatMessageMapper.updateEmbedding(messageId, embedding);
                log.debug("消息 embedding 生成完成: messageId={}", messageId);
            }
        } catch (Exception e) {
            log.error("消息 embedding 生成失败: messageId={}", messageId, e);
        }
    }

    /**
     * 异步触发记忆提炼（Record 链路）
     */
    @Async
    public void extractAndSaveMemoriesAsync(Integer petId, String userMessage, String aiResponse) {
        try {
            log.info("开始异步提炼记忆: petId={}", petId);

            List<Map<String, Object>> memories = memoryExtractor.extractMemories(userMessage, aiResponse);

            if (memories != null && !memories.isEmpty()) {
                log.info("提取到 {} 条记忆", memories.size());
                memoryService.saveMemories(petId, memories);
                log.info("记忆保存完成: petId={}, count={}", petId, memories.size());
            } else {
                log.debug("本轮对话无需存储记忆: petId={}", petId);
            }
        } catch (Exception e) {
            log.error("异步记忆提炼失败: petId={}", petId, e);
        }
    }
}
