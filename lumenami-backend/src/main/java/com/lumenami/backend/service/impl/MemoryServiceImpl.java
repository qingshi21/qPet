package com.lumenami.backend.service.impl;

import com.lumenami.backend.exception.BusinessException;
import com.lumenami.backend.mapper.PetMapper;
import com.lumenami.backend.mapper.PetMemoryMapper;
import com.lumenami.backend.model.Pet;
import com.lumenami.backend.model.PetMemory;
import com.lumenami.backend.service.MemoryService;
import com.lumenami.backend.service.QwenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 记忆服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryServiceImpl implements MemoryService {

    private final PetMemoryMapper petMemoryMapper;
    private final PetMapper petMapper;
    private final QwenService qwenService;

    @Override
    public void saveMemory(Integer petId, String key, String value, String type, String level, BigDecimal importance) {
        try {
            // 查找是否已存在该key的记忆
            PetMemory existing = petMemoryMapper.findLatestByKey(petId, key);

            PetMemory memory = new PetMemory();
            memory.setPetId(petId);
            memory.setMemoryKey(key);
            memory.setMemoryValue(value);
            memory.setType(PetMemory.MemoryType.valueOf(type.toUpperCase()));
            memory.setLevel(PetMemory.MemoryLevel.valueOf(level != null ? level.toUpperCase() : "LONG_TERM"));
            memory.setState(PetMemory.MemoryState.ACTIVE);

            // importance 归一化到 0-1，同时作为初始权重
            BigDecimal imp = (importance != null) ? importance : new BigDecimal("0.50");
            if (imp.compareTo(BigDecimal.ONE) > 0) {
                // 兼容旧的 1-10 范围，自动归一化
                imp = imp.divide(BigDecimal.TEN, 2, java.math.RoundingMode.HALF_UP);
            }
            memory.setImportance(imp);
            memory.setWeight(imp); // 初始权重 = importance
            memory.setAccessCount(0);

            if (existing != null) {
                // 如果值不同，创建新版本并保留旧版本
                if (!existing.getMemoryValue().equals(value)) {
                    memory.setVersion(existing.getVersion() + 1);
                    memory.setPreviousValue(existing.getMemoryValue());
                    log.info("更新记忆: petId={}, key={}, v{} -> v{}, old={}, new={}",
                        petId, key, existing.getVersion(), memory.getVersion(),
                        existing.getMemoryValue(), value);
                } else {
                    // 值相同，不重复插入
                    log.debug("记忆未变化，跳过: petId={}, key={}, value={}", petId, key, value);
                    return;
                }
            } else {
                memory.setVersion(1);
                memory.setPreviousValue(null);
            }

            petMemoryMapper.insert(memory);
            log.info("保存记忆成功: petId={}, key={}, type={}, level={}, importance={}, version={}",
                petId, key, type, level, imp, memory.getVersion());

            // 异步生成记忆 embedding（key + value 拼接）
            generateMemoryEmbeddingAsync(memory.getId(), key + ": " + value);

            // 检查是否需要自动总结（version > 3）
            if (memory.getVersion() > 3) {
                autoSummarizeAsync(petId, key, memory.getLevel());
            }
        } catch (Exception e) {
            log.error("保存记忆失败: petId={}, key={}", petId, key, e);
        }
    }

    @Override
    public void saveMemories(Integer petId, List<Map<String, Object>> memories) {
        if (memories == null || memories.isEmpty()) {
            return;
        }

        for (Map<String, Object> memory : memories) {
            String key = (String) memory.get("key");
            String value = (String) memory.get("value");
            String type = (String) memory.get("type");
            String level = (String) memory.get("level");

            // importance 可能是 Integer（旧格式）或 BigDecimal/Double（新格式）
            BigDecimal importance;
            Object impObj = memory.get("importance");
            if (impObj instanceof BigDecimal) {
                importance = (BigDecimal) impObj;
            } else if (impObj instanceof Number) {
                importance = BigDecimal.valueOf(((Number) impObj).doubleValue());
            } else {
                importance = new BigDecimal("0.50");
            }

            saveMemory(petId, key, value, type, level, importance);
        }
    }

    @Override
    public List<PetMemory> getMemories(Integer petId) {
        log.debug("查询活跃记忆列表: petId={}", petId);
        return petMemoryMapper.findActiveByPetId(petId);
    }

    @Override
    public List<PetMemory> getPermanentMemories(Integer petId) {
        log.debug("查询永久记忆: petId={}", petId);
        return petMemoryMapper.findPermanentByPetId(petId);
    }

    @Override
    public List<PetMemory> getTimelinessStatusMemories(Integer petId) {
        // 12小时内的 STATUS 类型记忆，且 weight > 0.6
        LocalDateTime since = LocalDateTime.now().minusHours(12);
        BigDecimal weightThreshold = new BigDecimal("0.6");
        log.debug("查询时效性状态记忆: petId={}, since={}", petId, since);
        return petMemoryMapper.findTimelinessStatus(petId, since, weightThreshold);
    }

    @Override
    public List<PetMemory> getMemoriesByType(Integer petId, String type) {
        // 从活跃记忆中按类型过滤
        List<PetMemory> all = petMemoryMapper.findActiveByPetId(petId);
        PetMemory.MemoryType memType = PetMemory.MemoryType.valueOf(type.toUpperCase());
        List<PetMemory> filtered = new ArrayList<>();
        for (PetMemory m : all) {
            if (m.getType() == memType) {
                filtered.add(m);
            }
        }
        return filtered;
    }

    @Override
    public PetMemory getLatestMemory(Integer petId, String key) {
        return petMemoryMapper.findLatestByKey(petId, key);
    }

    @Override
    public List<PetMemory> getMemoryHistory(Integer petId, String key) {
        log.debug("查询记忆历史: petId={}, key={}", petId, key);
        return petMemoryMapper.findAllVersionsByKey(petId, key);
    }

    @Override
    public void deleteMemory(Integer userId, Integer memoryId) {
        log.info("删除记忆请求: userId={}, memoryId={}", userId, memoryId);

        PetMemory memory = petMemoryMapper.findById(memoryId);
        if (memory == null) {
            log.warn("删除记忆失败，记忆不存在: memoryId={}", memoryId);
            throw new BusinessException(404, "记忆不存在");
        }

        verifyPetOwnership(userId, memory.getPetId());

        petMemoryMapper.deleteById(memoryId);
        log.info("删除记忆成功: userId={}, memoryId={}", userId, memoryId);
    }

    @Override
    public void updateMemory(Integer userId, Integer memoryId, String value, BigDecimal importance) {
        log.info("更新记忆请求: userId={}, memoryId={}", userId, memoryId);

        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(400, "记忆值不能为空");
        }

        PetMemory existing = petMemoryMapper.findById(memoryId);
        if (existing == null) {
            log.warn("更新记忆失败，记忆不存在: memoryId={}", memoryId);
            throw new BusinessException(404, "记忆不存在");
        }

        verifyPetOwnership(userId, existing.getPetId());

        existing.setMemoryValue(value);
        if (importance != null) {
            existing.setImportance(importance);
            existing.setWeight(importance); // 同步更新权重
        }
        petMemoryMapper.update(existing);
        log.info("更新记忆成功: userId={}, memoryId={}, value={}", userId, memoryId, value);
    }

    @Override
    public String buildMemoryContext(Integer petId) {
        // 注：此方法在 P2 阶段会重写为分层注入逻辑（L1/L2/L3）
        // 当前保持兼容：将所有活跃记忆按类型分组输出
        List<PetMemory> memories = getMemories(petId);
        if (memories == null || memories.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== 用户记忆 ===\n");

        // 过滤掉旧版本（只保留每个key的最新版本）
        Map<String, PetMemory> latestMemories = new java.util.HashMap<>();
        for (PetMemory memory : memories) {
            String key = memory.getMemoryKey();
            if (!latestMemories.containsKey(key) || memory.getVersion() > latestMemories.get(key).getVersion()) {
                latestMemories.put(key, memory);
            }
        }

        // 按类型分组展示
        Map<String, List<PetMemory>> groupedByType = new java.util.HashMap<>();
        for (PetMemory.MemoryType type : PetMemory.MemoryType.values()) {
            groupedByType.put(type.name(), new ArrayList<>());
        }

        for (PetMemory memory : latestMemories.values()) {
            groupedByType.get(memory.getType().name()).add(memory);
        }

        for (PetMemory.MemoryType type : PetMemory.MemoryType.values()) {
            List<PetMemory> typeMemories = groupedByType.get(type.name());
            if (!typeMemories.isEmpty()) {
                sb.append("\n").append(getTypeLabel(type)).append("：\n");
                for (PetMemory memory : typeMemories) {
                    String displayText = formatMemoryDisplay(memory);
                    sb.append("- ").append(displayText).append("\n");
                }
            }
        }

        sb.append("=============\n");
        return sb.toString();
    }

    private String formatMemoryDisplay(PetMemory memory) {
        String key = memory.getMemoryKey();
        String value = memory.getMemoryValue();

        Map<String, String> displayMap = new java.util.HashMap<>();
        displayMap.put("user_name", "我的名字是");
        displayMap.put("user_age", "我今年");
        displayMap.put("user_job", "我的职业是");
        displayMap.put("user_birthday", "我的生日是");
        displayMap.put("favorite_food", "我喜欢吃");
        displayMap.put("favorite_color", "我喜欢颜色");
        displayMap.put("favorite_music", "我喜欢听");
        displayMap.put("favorite_movie", "我喜欢看");
        displayMap.put("hobby", "我的爱好是");
        displayMap.put("current_project", "我正在做的项目是");
        displayMap.put("learning_goal", "我正在学习");
        displayMap.put("communication_style", "我喜欢的沟通方式是");

        for (Map.Entry<String, String> entry : displayMap.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue() + value;
            }
        }

        return key.replace("_", " ") + ": " + value;
    }

    private String getTypeLabel(PetMemory.MemoryType type) {
        switch (type) {
            case PROFILE: return "📋 用户画像";
            case PROJECT: return "📁 项目信息";
            case PREFERENCE: return "❤️ 偏好设置";
            case STATUS: return "🌤️ 用户状态";
            default: return "📝 其他记忆";
        }
    }

    @Override
    public void verifyPetOwnership(Integer userId, Integer petId) {
        if (userId == null || petId == null) {
            throw new BusinessException(400, "参数不完整");
        }
        Pet pet = petMapper.findById(petId);
        if (pet == null) {
            throw new BusinessException(404, "宠物不存在");
        }
        if (!pet.getUserId().equals(userId)) {
            log.warn("用户无权访问该宠物: userId={}, petId={}, ownerId={}", userId, petId, pet.getUserId());
            throw new BusinessException(403, "无权访问该宠物");
        }
    }

    /**
     * 生成记忆的 embedding 向量
     * 注：该方法在 extractAndSaveMemoriesAsync 异步线程中调用，不会阻塞主流程
     */
    public void generateMemoryEmbeddingAsync(Integer memoryId, String text) {
        try {
            String embedding = qwenService.embedding(text);
            if (embedding != null) {
                petMemoryMapper.updateEmbedding(memoryId, embedding);
                log.debug("记忆 embedding 生成完成: memoryId={}", memoryId);
            }
        } catch (Exception e) {
            log.error("记忆 embedding 生成失败: memoryId={}", memoryId, e);
        }
    }

    /**
     * 自动总结：当同一 key 的 version > 3 时触发
     * 1. 取出所有版本
     * 2. 调用 Qwen 生成总结
     * 3. 保存总结记忆（key_summary）
     * 4. 清理中间版本（保留最新一条原始记忆 + 总结记忆）
     */
    public void autoSummarizeAsync(Integer petId, String key, PetMemory.MemoryLevel originalLevel) {
        try {
            String summaryKey = key + "_summary";

            // 检查 summary 是否已存在
            PetMemory existingSummary = petMemoryMapper.findLatestByKey(petId, summaryKey);

            // 获取所有版本的值
            List<PetMemory> allVersions = petMemoryMapper.findAllVersionsByKey(petId, key);
            if (allVersions.size() <= 3) {
                return;
            }

            // 收集所有版本的值（按时间正序）
            List<String> values = new ArrayList<>();
            for (PetMemory m : allVersions) {
                values.add(m.getMemoryValue());
            }

            // 调用 Qwen 总结
            String summaryValue = qwenService.summarize(key, values);
            if (summaryValue == null || summaryValue.trim().isEmpty()) {
                log.warn("自动总结失败: petId={}, key={}", petId, key);
                return;
            }

            // 确定总结记忆的级别（继承原记忆的最高级别）
            PetMemory.MemoryLevel summaryLevel = originalLevel;
            if (existingSummary != null) {
                // summary 已存在，取两者中更高的级别
                summaryLevel = getHigherLevel(originalLevel, existingSummary.getLevel());
            }

            if (existingSummary != null) {
                // 更新已有总结
                existingSummary.setMemoryValue(summaryValue);
                existingSummary.setVersion(existingSummary.getVersion() + 1);
                existingSummary.setLevel(summaryLevel);
                petMemoryMapper.update(existingSummary);
                log.info("更新记忆总结: petId={}, summaryKey={}, version={}", petId, summaryKey, existingSummary.getVersion());
            } else {
                // 新建总结记忆
                PetMemory summary = new PetMemory();
                summary.setPetId(petId);
                summary.setMemoryKey(summaryKey);
                summary.setMemoryValue(summaryValue);
                summary.setType(allVersions.get(0).getType()); // 继承原类型
                summary.setLevel(summaryLevel);
                summary.setState(PetMemory.MemoryState.ACTIVE);
                summary.setImportance(allVersions.get(0).getImportance()); // 继承原 importance
                summary.setWeight(allVersions.get(0).getImportance());
                summary.setAccessCount(0);
                summary.setVersion(1);
                petMemoryMapper.insert(summary);
                log.info("创建记忆总结: petId={}, summaryKey={}", petId, summaryKey);

                // 生成总结记忆的 embedding
                generateMemoryEmbeddingAsync(summary.getId(), summaryKey + ": " + summaryValue);
            }

            // 清理中间版本：保留最新一条原始记忆，删除其余
            // allVersions 按 version DESC 排序，第一个是最新的
            for (int i = 1; i < allVersions.size(); i++) {
                petMemoryMapper.physicalDeleteById(allVersions.get(i).getId());
                log.debug("清理中间版本: id={}, key={}, version={}",
                    allVersions.get(i).getId(), key, allVersions.get(i).getVersion());
            }

        } catch (Exception e) {
            log.error("自动总结失败: petId={}, key={}", petId, key, e);
        }
    }

    private PetMemory.MemoryLevel getHigherLevel(PetMemory.MemoryLevel a, PetMemory.MemoryLevel b) {
        // PERMANENT > LONG_TERM > SHORT_TERM
        if (a == PetMemory.MemoryLevel.PERMANENT || b == PetMemory.MemoryLevel.PERMANENT) return PetMemory.MemoryLevel.PERMANENT;
        if (a == PetMemory.MemoryLevel.LONG_TERM || b == PetMemory.MemoryLevel.LONG_TERM) return PetMemory.MemoryLevel.LONG_TERM;
        return PetMemory.MemoryLevel.SHORT_TERM;
    }
}
