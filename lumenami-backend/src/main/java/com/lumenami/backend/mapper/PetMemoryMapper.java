package com.lumenami.backend.mapper;

import com.lumenami.backend.model.PetMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PetMemoryMapper {

    void insert(PetMemory memory);

    List<PetMemory> findByPetId(@Param("petId") Integer petId);

    List<PetMemory> findByPetIdAndType(@Param("petId") Integer petId, @Param("type") String type);

    /**
     * 查找某个key的最新版本记忆
     */
    PetMemory findLatestByKey(@Param("petId") Integer petId, @Param("memoryKey") String memoryKey);

    /**
     * 查找某个key的所有历史版本
     */
    List<PetMemory> findAllVersionsByKey(@Param("petId") Integer petId, @Param("memoryKey") String memoryKey);

    void deleteByPetId(@Param("petId") Integer petId);

    void deleteById(@Param("id") Integer id);

    /**
     * 根据ID查找记忆
     */
    PetMemory findById(@Param("id") Integer id);

    void update(PetMemory memory);
}
