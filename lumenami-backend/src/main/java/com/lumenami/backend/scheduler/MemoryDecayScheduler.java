package com.lumenami.backend.scheduler;

import com.lumenami.backend.mapper.PetMemoryMapper;
import com.lumenami.backend.model.PetMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 记忆衰减定时任务
 * 每天凌晨 3 点执行：
 * 1. 计算所有活跃非永久记忆的衰减权重
 * 2. 将 weight < 0.2 的记忆归档
 * 3. 将归档超过 30 天的记忆标记为 DELETED
 * 4. 物理删除 DELETED 超过 30 天的记忆
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryDecayScheduler {

    private final PetMemoryMapper petMemoryMapper;

    // 衰减系数
    private static final BigDecimal LAMBDA_LONG_TERM = new BigDecimal("0.01");
    private static final BigDecimal LAMBDA_SHORT_TERM = new BigDecimal("0.1");
    private static final BigDecimal ARCHIVE_THRESHOLD = new BigDecimal("0.2");

    // 状态流转天数
    private static final int ARCHIVED_TO_DELETED_DAYS = 30;
    private static final int DELETED_TO_PHYSICAL_DAYS = 30;

    /**
     * 每天凌晨 3 点执行衰减计算
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void processDecay() {
        log.info("===== 记忆衰减定时任务开始 =====");

        try {
            // 1. 衰减计算：重新计算所有活跃非永久记忆的 weight
            int decayCount = calculateDecay();
            log.info("衰减计算完成，处理 {} 条记忆", decayCount);

            // 2. 归档：weight < 0.2 的活跃记忆 → ARCHIVED
            int archiveCount = archiveMemories();
            log.info("归档完成，归档 {} 条记忆", archiveCount);

            // 3. 状态流转：ARCHIVED 超过 30 天 → DELETED
            int deletedCount = transitionArchivedToDeleted();
            log.info("ARCHIVED→DELETED 转换完成，转换 {} 条记忆", deletedCount);

            // 4. 物理删除：DELETED 超过 30 天 → 物理移除
            int physicalDeleteCount = physicalDeleteExpired();
            log.info("物理删除完成，删除 {} 条记忆", physicalDeleteCount);

        } catch (Exception e) {
            log.error("记忆衰减定时任务执行失败", e);
        }

        log.info("===== 记忆衰减定时任务结束 =====");
    }

    /**
     * 计算所有活跃非永久记忆的衰减权重
     * 公式：final_weight = importance × e^(-λ × days) × (1.0 + 0.1 × min(access_count, 5)) + 0.05 × min(access_count, 6)
     */
    private int calculateDecay() {
        List<PetMemory> decableMemories = petMemoryMapper.findDecayable();
        int count = 0;

        for (PetMemory memory : decableMemories) {
            // 计算距今天数
            long days = ChronoUnit.DAYS.between(memory.getCreatedAt(), LocalDateTime.now());
            if (days < 0) days = 0;

            // 选择衰减系数
            BigDecimal lambda = (memory.getLevel() == PetMemory.MemoryLevel.SHORT_TERM)
                    ? LAMBDA_SHORT_TERM : LAMBDA_LONG_TERM;

            // time_decay_weight = importance × e^(-λ × days)
            double timeDecay = memory.getImportance().doubleValue() * Math.exp(-lambda.doubleValue() * days);

            // access_decay_weight = 1.0 + 0.1 × min(access_count, 5)
            int accessCount = memory.getAccessCount() != null ? memory.getAccessCount() : 0;
            double accessDecay = 1.0 + 0.1 * Math.min(accessCount, 5);

            // access_bonus = 0.05 × min(access_count, 6)
            double accessBonus = 0.05 * Math.min(accessCount, 6);

            // final_weight = time_decay × access_decay + access_bonus
            double finalWeight = timeDecay * accessDecay + accessBonus;

            // 限制在 0-1 范围
            finalWeight = Math.max(0.0, Math.min(1.0, finalWeight));

            BigDecimal newWeight = BigDecimal.valueOf(finalWeight).setScale(3, RoundingMode.HALF_UP);

            // 更新权重（状态暂不变，归档步骤统一处理）
            petMemoryMapper.updateWeightAndState(memory.getId(), newWeight, "ACTIVE", null, null);
            count++;
        }

        return count;
    }

    /**
     * 将 weight < 0.2 的活跃记忆归档
     */
    private int archiveMemories() {
        List<PetMemory> archivable = petMemoryMapper.findArchivable(ARCHIVE_THRESHOLD);
        int count = 0;

        for (PetMemory memory : archivable) {
            petMemoryMapper.updateWeightAndState(
                    memory.getId(),
                    memory.getWeight(),
                    "ARCHIVED",
                    LocalDateTime.now(),
                    null
            );
            count++;
            log.debug("记忆归档: id={}, key={}, weight={}", memory.getId(), memory.getMemoryKey(), memory.getWeight());
        }

        return count;
    }

    /**
     * ARCHIVED 超过 30 天 → DELETED
     */
    private int transitionArchivedToDeleted() {
        List<PetMemory> expired = petMemoryMapper.findExpiredArchived(ARCHIVED_TO_DELETED_DAYS);
        int count = 0;

        for (PetMemory memory : expired) {
            petMemoryMapper.updateWeightAndState(
                    memory.getId(),
                    memory.getWeight(),
                    "DELETED",
                    memory.getArchivedAt(),
                    LocalDateTime.now()
            );
            count++;
            log.debug("ARCHIVED→DELETED: id={}, key={}", memory.getId(), memory.getMemoryKey());
        }

        return count;
    }

    /**
     * DELETED 超过 30 天 → 物理删除
     */
    private int physicalDeleteExpired() {
        List<PetMemory> expired = petMemoryMapper.findExpiredDeleted(DELETED_TO_PHYSICAL_DAYS);
        int count = 0;

        for (PetMemory memory : expired) {
            petMemoryMapper.physicalDeleteById(memory.getId());
            count++;
            log.debug("物理删除记忆: id={}, key={}", memory.getId(), memory.getMemoryKey());
        }

        return count;
    }
}
