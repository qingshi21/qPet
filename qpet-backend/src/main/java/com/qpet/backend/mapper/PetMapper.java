package com.qpet.backend.mapper;

import com.qpet.backend.model.Pet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PetMapper {

    // 插入宠物
    void insert(Pet pet);

    // 根据用户ID查询该用户所有宠物
    List<Pet> findByUserId(Integer userId);

    // 根据宠物ID查询
    Pet findById(Integer petId);

    // 取消该用户所有宠物的激活状态
    void deactivateAllByUserId(Integer userId);

    // 激活指定宠物（将其 is_active 设为 1）
    void activate(Integer petId);

    // 删除宠物
    void deleteById(Integer petId);

    // 检查该用户下是否已有同名宠物
    int countByNameAndUserId(@Param("userId") Integer userId, @Param("name") String name);
}