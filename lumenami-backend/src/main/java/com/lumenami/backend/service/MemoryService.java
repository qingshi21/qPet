package com.lumenami.backend.service;

import com.lumenami.backend.model.PetMemory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 记忆服务接口
 */
public interface MemoryService {

    /**
     * 保存单条记忆（带版本管理）
     */
    void saveMemory(Integer petId, String key, String value, String type, String level, BigDecimal importance);

    /**
     * 批量保存记忆
     */
    void saveMemories(Integer petId, List<Map<String, Object>> memories);

    /**
     * 获取宠物的所有活跃记忆
     */
    List<PetMemory> getMemories(Integer petId);

    /**
     * 获取宠物的永久记忆（L1固定层）
     */
    List<PetMemory> getPermanentMemories(Integer petId);

    /**
     * 获取时效性状态记忆（12h内，高权重）
     */
    List<PetMemory> getTimelinessStatusMemories(Integer petId);

    /**
     * 根据类型获取记忆
     */
    List<PetMemory> getMemoriesByType(Integer petId, String type);

    /**
     * 获取某个key的最新版本记忆
     */
    PetMemory getLatestMemory(Integer petId, String key);

    /**
     * 获取某个key的所有历史版本
     */
    List<PetMemory> getMemoryHistory(Integer petId, String key);

    /**
     * 删除记忆
     */
    void deleteMemory(Integer userId, Integer memoryId);

    /**
     * 更新记忆
     */
    void updateMemory(Integer userId, Integer memoryId, String value, BigDecimal importance);

    /**
     * 构建记忆的文本描述（用于拼入 System Prompt）
     * 注：P2阶段会重写为分层注入逻辑
     */
    String buildMemoryContext(Integer petId);

    /**
     * 校验用户是否拥有该宠物
     */
    void verifyPetOwnership(Integer userId, Integer petId);
}
