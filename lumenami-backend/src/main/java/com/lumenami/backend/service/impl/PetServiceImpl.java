package com.lumenami.backend.service.impl;

import com.lumenami.backend.dto.CreatePetRequest;
import com.lumenami.backend.dto.PetResponse;
import com.lumenami.backend.exception.BusinessException;
import com.lumenami.backend.mapper.PetMapper;
import com.lumenami.backend.model.Pet;
import com.lumenami.backend.service.PetService;
import com.lumenami.backend.service.QwenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 宠物服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetServiceImpl implements PetService {

    private final PetMapper petMapper;
    private final QwenService qwenService;

    @Override
    @Transactional
    public PetResponse createPet(Integer userId, CreatePetRequest request) {
        log.info("创建宠物请求: userId={}, name={}", userId, request.getName());
        
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new BusinessException(400, "宠物名称不能为空");
        }
        if (request.getSystemPrompt() == null || request.getSystemPrompt().trim().isEmpty()) {
            throw new BusinessException(400, "System Prompt 不能为空");
        }

        int count = petMapper.countByNameAndUserId(userId, request.getName());
        if (count > 0) {
            log.warn("创建宠物失败，名称已存在: userId={}, name={}", userId, request.getName());
            throw new BusinessException(400, "已存在同名宠物");
        }

        Pet pet = new Pet();
        pet.setUserId(userId);
        pet.setName(request.getName());
        pet.setRoleName(request.getRoleName());
        pet.setSystemPrompt(request.getSystemPrompt());
        pet.setIsActive(0);
        petMapper.insert(pet);

        log.info("创建宠物成功: petId={}, name={}, userId={}", pet.getId(), pet.getName(), userId);
        return toResponse(pet);
    }

    @Override
    public List<PetResponse> getPetsByUserId(Integer userId) {
        log.debug("查询用户宠物列表: userId={}", userId);
        List<PetResponse> pets = petMapper.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        log.debug("查询用户宠物列表完成: userId={}, count={}", userId, pets.size());
        return pets;
    }

    @Override
    @Transactional
    public PetResponse switchPet(Integer userId, Integer petId) {
        log.info("切换宠物请求: userId={}, petId={}", userId, petId);
        
        Pet pet = petMapper.findById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            log.warn("切换宠物失败，宠物不存在: userId={}, petId={}", userId, petId);
            throw new BusinessException(404, "宠物不存在");
        }

        petMapper.deactivateAllByUserId(userId);
        petMapper.activate(petId);

        Pet updated = petMapper.findById(petId);
        log.info("切换宠物成功: userId={}, petId={}, petName={}", userId, petId, updated.getName());
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void deletePet(Integer userId, Integer petId) {
        log.info("删除宠物请求: userId={}, petId={}", userId, petId);
        
        Pet pet = petMapper.findById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            log.warn("删除宠物失败，宠物不存在: userId={}, petId={}", userId, petId);
            throw new BusinessException(404, "宠物不存在");
        }
        petMapper.deleteById(petId);
        
        log.info("删除宠物成功: userId={}, petId={}", userId, petId);
    }

    private PetResponse toResponse(Pet pet) {
        PetResponse resp = new PetResponse();
        resp.setPetId(pet.getId());
        resp.setName(pet.getName());
        resp.setRoleName(pet.getRoleName());
        resp.setIsActive(pet.getIsActive() == 1);
        return resp;
    }

    @Override
    public String generateRoleUnderstanding(String name, String roleName, String description) {
        log.info("生成角色理解请求: name={}, roleName={}", name, roleName);

        // 构建角色理解的 system prompt
        String understandingPrompt = buildRoleUnderstandingPrompt(name, roleName, description);
        
        // 调用 Qwen 生成角色理解
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "请帮我理解并描述这个角色。");
        messages.add(userMsg);
        
        String result = qwenService.chat(understandingPrompt, messages);
        log.info("角色理解生成完成: name={}, resultLength={}", name, result.length());
        return result;
    }

    /**
     * 构建角色理解的 prompt
     */
    private String buildRoleUnderstandingPrompt(String name, String roleName, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个角色分析专家。请根据以下信息，生成一段详细的角色理解文段。\n\n");
        
        if (roleName != null && !roleName.trim().isEmpty()) {
            sb.append("【角色名称】").append(roleName).append("\n");
            sb.append("请根据你的知识，详细描述这个角色的：\n");
            sb.append("- 性格特点（核心性格、情绪倾向）\n");
            sb.append("- 言行风格（说话方式、常用语气词、口头禅）\n");
            sb.append("- 语调特征（温柔/冷淡/活泼/沉稳等）\n");
            sb.append("- 人际关系倾向（如何对待朋友、陌生人）\n");
            sb.append("- 价值观和信念\n");
            sb.append("- 其他显著特征\n\n");
        } else {
            sb.append("【注意】没有指定角色名称，请根据昵称和描述来推断角色特征。\n\n");
        }
        
        sb.append("【用户给角色的昵称】").append(name != null ? name : "未设置").append("\n");
        
        if (description != null && !description.trim().isEmpty()) {
            sb.append("【用户提供的描述】\n").append(description).append("\n\n");
        }
        
        sb.append("\n【输出要求】\n");
        sb.append("请输出一段完整的角色理解文段，格式如下：\n");
        sb.append("1. 先用一句话概括角色核心\n");
        sb.append("2. 然后分段描述性格、言行、语调等特征\n");
        sb.append("3. 语言要具体、可操作，能直接用作 AI 角色设定\n");
        sb.append("4. 不要写废话，每句话都要有信息量\n");
        sb.append("5. 总长度控制在 300-500 字，也可以按照理解来动态调整长度\n");
        
        return sb.toString();
    }
}
