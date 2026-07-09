package com.lumenami.backend.service;

import com.lumenami.backend.mapper.PetMemoryMapper;
import com.lumenami.backend.model.PetMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final PetMemoryMapper petMemoryMapper;

    /**
     * 保存记忆（带版本管理）
     */
    public void saveMemory(Integer petId, String key, String value, String type, Integer importance) {
        try {
            // 查找是否已存在该key的记忆
            PetMemory existing = petMemoryMapper.findLatestByKey(petId, key);
            
            PetMemory memory = new PetMemory();
            memory.setPetId(petId);
            memory.setMemoryKey(key);
            memory.setMemoryValue(value);
            memory.setType(PetMemory.MemoryType.valueOf(type.toUpperCase()));
            memory.setImportance(importance != null ? importance : 5);
            
            // 判断是否为永久记忆（某些类型的记忆永不衰减）
            memory.setIsPermanent(isPermanentMemory(key, type));
            
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
            log.info("保存记忆成功: petId={}, key={}, type={}, version={}, permanent={}", 
                petId, key, type, memory.getVersion(), memory.getIsPermanent());
        } catch (Exception e) {
            log.error("保存记忆失败: petId={}, key={}", petId, key, e);
        }
    }

    /**
     * 判断是否为永久记忆（不衰减）
     */
    private boolean isPermanentMemory(String key, String type) {
        // 用户基本信息类记忆为永久记忆
        if ("PROFILE".equalsIgnoreCase(type)) {
            // 姓名、职业等关键信息设为永久记忆
            return key.contains("name") || key.contains("job") || key.contains("profession") || 
                   key.contains("age") || key.contains("birthday");
        }
        return false;
    }

    /**
     * 批量保存记忆
     */
    public void saveMemories(Integer petId, List<Map<String, Object>> memories) {
        if (memories == null || memories.isEmpty()) {
            return;
        }
        
        for (Map<String, Object> memory : memories) {
            String key = (String) memory.get("key");
            String value = (String) memory.get("value");
            String type = (String) memory.get("type");
            Integer importance = (Integer) memory.get("importance");
            
            saveMemory(petId, key, value, type, importance);
        }
    }

    /**
     * 获取宠物的所有记忆
     */
    public List<PetMemory> getMemories(Integer petId) {
        return petMemoryMapper.findByPetId(petId);
    }

    /**
     * 根据类型获取记忆
     */
    public List<PetMemory> getMemoriesByType(Integer petId, String type) {
        return petMemoryMapper.findByPetIdAndType(petId, type);
    }

    /**
     * 获取某个key的最新版本记忆
     */
    public PetMemory getLatestMemory(Integer petId, String key) {
        return petMemoryMapper.findLatestByKey(petId, key);
    }

    /**
     * 获取某个key的所有历史版本
     */
    public List<PetMemory> getMemoryHistory(Integer petId, String key) {
        return petMemoryMapper.findAllVersionsByKey(petId, key);
    }

    /**
     * 删除记忆（手动修正接口）
     */
    public void deleteMemory(Integer memoryId) {
        try {
            petMemoryMapper.deleteById(memoryId);
            log.info("删除记忆成功: id={}", memoryId);
        } catch (Exception e) {
            log.error("删除记忆失败: id={}", memoryId, e);
        }
    }

    /**
     * 更新记忆（手动修正接口）
     */
    public void updateMemory(Integer memoryId, String value, Integer importance) {
        try {
            PetMemory existing = petMemoryMapper.findById(memoryId);
            if (existing == null) {
                log.warn("记忆不存在: id={}", memoryId);
                return;
            }
            
            existing.setMemoryValue(value);
            existing.setImportance(importance);
            petMemoryMapper.update(existing);
            log.info("更新记忆成功: id={}, value={}, importance={}", memoryId, value, importance);
        } catch (Exception e) {
            log.error("更新记忆失败: id={}", memoryId, e);
        }
    }

    /**
     * 构建记忆的文本描述（用于拼入 System Prompt）
     */
    public String buildMemoryContext(Integer petId) {
        List<PetMemory> memories = getMemories(petId);
        if (memories == null || memories.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== 用户记忆 ===\n");
        
        // 按类型分组，并过滤掉旧版本（只保留每个key的最新版本）
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
        
        // 输出各类记忆
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

    /**
     * 格式化记忆显示文本（用户友好）
     */
    private String formatMemoryDisplay(PetMemory memory) {
        // 根据key生成友好的显示文本
        String key = memory.getMemoryKey();
        String value = memory.getMemoryValue();
        
        // 常见key的友好显示映射
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
        
        // 尝试匹配已知key
        for (Map.Entry<String, String> entry : displayMap.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue() + value;
            }
        }
        
        // 默认显示格式
        return key.replace("_", " ") + ": " + value;
    }

    private String getTypeLabel(PetMemory.MemoryType type) {
        switch (type) {
            case PROFILE: return "📋 用户画像";
            case PROJECT: return "💼 项目信息";
            case PREFERENCE: return "❤️ 偏好设置";
            default: return "📝 其他记忆";
        }
    }
}
