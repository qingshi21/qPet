package com.lumenami.backend.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PetMemory {
    private Integer id;
    private Integer petId;
    private String memoryKey;
    private String memoryValue;
    private MemoryType type;
    private MemoryLevel level;
    private MemoryState state;
    private BigDecimal importance;     // 重要性评分（0-1），同时作为衰减计算的初始权重
    private BigDecimal weight;         // 当前权重（0-1），由衰减公式动态计算
    private Integer accessCount;       // 被访问次数
    private String embedding;          // Qwen embedding 向量（JSON字符串）
    private Integer version;           // 版本号
    private String previousValue;      // 上一个版本的值
    private LocalDateTime archivedAt;  // 归档时间
    private LocalDateTime deletedAt;   // 删除时间
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum MemoryType {
        PROFILE,      // 用户画像（姓名、职业等）
        PROJECT,      // 项目相关信息
        PREFERENCE,   // 用户偏好（喜欢的话题、风格等）
        STATUS        // 用户状态（心情、近期状态等）
    }

    public enum MemoryLevel {
        PERMANENT,    // 永久记忆（不衰减）
        LONG_TERM,    // 长期记忆（λ=0.01）
        SHORT_TERM    // 短期记忆（λ=0.1）
    }

    public enum MemoryState {
        ACTIVE,       // 活跃（参与检索）
        ARCHIVED,     // 归档（不参与检索）
        DELETED       // 已删除（等待物理清理）
    }
}