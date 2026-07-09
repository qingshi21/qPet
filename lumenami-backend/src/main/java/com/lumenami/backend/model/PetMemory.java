package com.lumenami.backend.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PetMemory {
    private Integer id;
    private Integer petId;
    private String memoryKey;
    private String memoryValue;
    private MemoryType type;
    private Integer importance;
    private Boolean isPermanent;      // 是否永久记忆（不衰减）
    private Integer version;           // 版本号
    private String previousValue;      // 上一个版本的值
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum MemoryType {
        PROFILE,      // 用户画像（姓名、职业等）
        PROJECT,      // 项目相关信息
        PREFERENCE    // 用户偏好（喜欢的话题、风格等）
    }
}
