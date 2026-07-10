package com.lumenami.backend.service;

import com.lumenami.backend.dto.CreatePetRequest;
import com.lumenami.backend.dto.PetResponse;

import java.util.List;

/**
 * 宠物服务接口
 */
public interface PetService {

    /**
     * 创建宠物
     * @param userId 用户ID
     * @param request 创建请求
     * @return 创建的宠物信息
     */
    PetResponse createPet(Integer userId, CreatePetRequest request);

    /**
     * 获取用户的所有宠物列表
     * @param userId 用户ID
     * @return 宠物列表
     */
    List<PetResponse> getPetsByUserId(Integer userId);

    /**
     * 切换当前激活的宠物
     * @param userId 用户ID
     * @param petId 宠物ID
     * @return 切换后的宠物信息
     */
    PetResponse switchPet(Integer userId, Integer petId);

    /**
     * 删除宠物
     * @param userId 用户ID
     * @param petId 宠物ID
     */
    void deletePet(Integer userId, Integer petId);

    /**
     * 生成角色理解文段（不依赖宠物ID，直接根据输入内容生成）
     * @param name 宠物名称
     * @param roleName 角色名称
     * @param description 用户描述
     * @return 角色理解文段
     */
    String generateRoleUnderstanding(String name, String roleName, String description);
}