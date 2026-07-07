package com.qpet.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.qpet.backend.BaseTest;
import com.qpet.backend.dto.CreatePetRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PetControllerTest extends BaseTest {

    /**
     * 测试正常创建宠物
     * 预期：返回 200，宠物名称与请求一致
     */
    @Test
    public void testCreatePet() throws Exception {
        CreatePetRequest request = new CreatePetRequest();
        request.setName("测试宠物");
        request.setSystemPrompt("这是一个测试宠物");

        mockMvc.perform(post("/api/pets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("测试宠物"));
    }

    /**
     * 测试创建同名宠物（应被拦截）
     * 预期：第二次请求返回 400，提示"已存在同名宠物"
     */
    @Test
    public void testCreatePet_duplicateName() throws Exception {
        CreatePetRequest request = new CreatePetRequest();
        request.setName("重复宠物");
        request.setSystemPrompt("这是一个测试宠物");

        // 第一次创建：成功
        mockMvc.perform(post("/api/pets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 第二次创建同名：应失败
        mockMvc.perform(post("/api/pets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("已存在同名宠物"));
    }

    /**
     * 测试获取宠物列表
     * 预期：返回 200，data 是数组
     */
    @Test
    public void testListPets() throws Exception {
        mockMvc.perform(get("/api/pets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    /**
     * 测试激活宠物
     * 流程：先创建一个宠物 → 解析真实 ID → 用该 ID 调用激活接口
     * 预期：激活成功，isActive 变为 true
     */
    @Test
    public void testActivatePet() throws Exception {
        // 1. 创建宠物
        CreatePetRequest request = new CreatePetRequest();
        request.setName("激活测试宠物");
        request.setSystemPrompt("用于测试激活");

        String responseJson = mockMvc.perform(post("/api/pets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 2. 用 Jackson 从响应中解析 petId
        JsonNode root = objectMapper.readTree(responseJson);
        int petId = root.get("data").get("petId").asInt();

        // 3. 激活该宠物
        mockMvc.perform(patch("/api/pets/{petId}/activation", petId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    /**
     * 测试删除宠物
     * 流程：先创建一个宠物 → 解析真实 ID → 用该 ID 调用删除接口
     * 预期：删除成功
     */
    @Test
    public void testDeletePet() throws Exception {
        // 1. 创建宠物
        CreatePetRequest request = new CreatePetRequest();
        request.setName("删除测试宠物");
        request.setSystemPrompt("用于测试删除");

        String responseJson = mockMvc.perform(post("/api/pets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 2. 用 Jackson 从响应中解析 petId
        JsonNode root = objectMapper.readTree(responseJson);
        int petId = root.get("data").get("petId").asInt();

        // 3. 删除该宠物
        mockMvc.perform(delete("/api/pets/{petId}", petId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("删除成功"));
    }
}