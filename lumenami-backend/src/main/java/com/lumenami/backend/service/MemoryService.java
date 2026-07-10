package com.lumenami.backend.service;

import com.lumenami.backend.model.PetMemory;

import java.util.List;
import java.util.Map;

/**
 * 记忆服务接口
 */
public interface MemoryService {

    /**
     * 保存记忆（带版本管理）
     * @param petId 宠物ID
     * @param key 记忆键名
     * @param value 记忆值
     * @param type 记忆类型
     * @param importance 重要性评分
     */
    void saveMemory(Integer petId, String key, String value, String type, Integer importance);

    /**
     * 批量保存记忆
     * @param petId 宠物ID
     * @param memories 记忆列表
     */
    void saveMemories(Integer petId, List<Map<String, Object>> memories);

    /**
     * 获取宠物的所有记忆
     * @param petId 宠物ID
     * @return 记忆列表
     */
    List<PetMemory> getMemories(Integer petId);

    /**
     * 根据类型获取记忆
     * @param petId 宠物ID
     * @param type 记忆类型
     * @return 记忆列表
     */
    List<PetMemory> getMemoriesByType(Integer petId, String type);

    /**
     * 获取某个key的最新版本记忆
     * @param petId 宠物ID
     * @param key 记忆键名
     * @return 最新版本记忆
     */
    PetMemory getLatestMemory(Integer petId, String key);

    /**
     * 获取某个key的所有历史版本
     * @param petId 宠物ID
     * @param key 记忆键名
     * @return 历史版本列表
     */
    List<PetMemory> getMemoryHistory(Integer petId, String key);

    /**
     * 删除记忆（手动修正接口）
     * @param userId 用户ID（权限校验）
     * @param memoryId 记忆ID
     */
    void deleteMemory(Integer userId, Integer memoryId);

    /**
     * 更新记忆（手动修正接口）
     * @param userId 用户ID（权限校验）
     * @param memoryId 记忆ID
     * @param value 新的记忆值
     * @param importance 新的重要性评分
     */
    void updateMemory(Integer userId, Integer memoryId, String value, Integer importance);

    /**
     * 构建记忆的文本描述（用于拼入 System Prompt）
     * @param petId 宠物ID
     * @return 记忆上下文文本
     */
    String buildMemoryContext(Integer petId);

    /**
     * 校验用户是否拥有该宠物
     * @param userId 用户ID
     * @param petId 宠物ID
     */
    void verifyPetOwnership(Integer userId, Integer petId);
}
