package com.lumenami.backend.mapper;

import com.lumenami.backend.model.PetMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PetMemoryMapper {

    void insert(PetMemory memory);

    void update(PetMemory memory);

    /**
     * 根据ID查找记忆
     */
    PetMemory findById(@Param("id") Integer id);

    /**
     * 查找某个key的最新版本记忆
     */
    PetMemory findLatestByKey(@Param("petId") Integer petId, @Param("memoryKey") String memoryKey);

    /**
     * 查找某个key的所有历史版本
     */
    List<PetMemory> findAllVersionsByKey(@Param("petId") Integer petId, @Param("memoryKey") String memoryKey);

    /**
     * 查找所有活跃记忆（用于检索）
     */
    List<PetMemory> findActiveByPetId(@Param("petId") Integer petId);

    /**
     * 查找永久记忆（L1固定层）
     */
    List<PetMemory> findPermanentByPetId(@Param("petId") Integer petId);

    /**
     * 查找时效性状态记忆（12h内，type=STATUS，state=ACTIVE，weight>阈值）
     */
    List<PetMemory> findTimelinessStatus(@Param("petId") Integer petId,
                                          @Param("since") LocalDateTime since,
                                          @Param("weightThreshold") BigDecimal weightThreshold);

    /**
     * 查找所有需要衰减计算的活跃记忆（ACTIVE + 非PERMANENT）
     */
    List<PetMemory> findDecayable();

    /**
     * 查找需要归档的记忆（weight < 阈值，state=ACTIVE，level!=PERMANENT）
     */
    List<PetMemory> findArchivable(@Param("weightThreshold") BigDecimal weightThreshold);

    /**
     * 查找需要转为deleted的归档记忆（archived_at超过指定天数）
     */
    List<PetMemory> findExpiredArchived(@Param("days") Integer days);

    /**
     * 查找需要物理删除的记忆（deleted_at超过指定天数）
     */
    List<PetMemory> findExpiredDeleted(@Param("days") Integer days);

    /**
     * 批量更新记忆权重和状态
     */
    void updateWeightAndState(@Param("id") Integer id,
                               @Param("weight") BigDecimal weight,
                               @Param("state") String state,
                               @Param("archivedAt") LocalDateTime archivedAt,
                               @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 更新访问次数
     */
    void incrementAccessCount(@Param("id") Integer id);

    /**
     * 更新embedding
     */
    void updateEmbedding(@Param("id") Integer id, @Param("embedding") String embedding);

    /**
     * 物理删除记忆
     */
    void physicalDeleteById(@Param("id") Integer id);

    void deleteByPetId(@Param("petId") Integer petId);

    void deleteById(@Param("id") Integer id);
}
