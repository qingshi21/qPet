package com.lumenami.backend.service;

import com.lumenami.backend.dto.ChatRequest;
import com.lumenami.backend.dto.ChatResponse;
import com.lumenami.backend.exception.BusinessException;
import com.lumenami.backend.mapper.ChatMessageMapper;
import com.lumenami.backend.mapper.PetMapper;
import com.lumenami.backend.model.ChatMessage;
import com.lumenami.backend.model.Pet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final PetMapper petMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final QwenService qwenService;

    /**
     * 处理聊天请求
     */
    @Transactional
    public ChatResponse chat(Integer userId, ChatRequest request) {
        // 获取宠物信息
        Pet pet = petMapper.findById(request.getPetId());
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(404, "宠物不存在");
        }

        // 保存用户消息到数据库
        ChatMessage userMsg = new ChatMessage();
        userMsg.setPetId(pet.getId());
        userMsg.setUserId(userId);
        userMsg.setRole("user");
        userMsg.setContent(request.getMessage());
        chatMessageMapper.insert(userMsg);

        // 从数据库加载完整对话历史
        List<ChatMessage> dbHistory = chatMessageMapper.findByPetId(pet.getId());

        // 构建发给 AI 的消息列表
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage msg : dbHistory) {
            Map<String, String> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            messages.add(m);
        }

        // 构建增强的 system prompt
        String systemPrompt = buildSystemPrompt(pet);

        // 调用 Qwen API
        String reply = qwenService.chat(systemPrompt, messages);

        // 保存 AI 回复到数据库
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setPetId(pet.getId());
        aiMsg.setUserId(userId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(reply);
        chatMessageMapper.insert(aiMsg);

        // 构建响应
        ChatResponse response = new ChatResponse();
        response.setReply(reply);
        return response;
    }

    /**
     * 获取宠物的对话历史
     */
    public List<ChatMessage> getHistory(Integer userId, Integer petId) {
        Pet pet = petMapper.findById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(404, "宠物不存在");
        }
        return chatMessageMapper.findByPetId(petId);
    }

    /**
     * 构建增强的 system prompt，包含宠物身份信息
     */
    private String buildSystemPrompt(Pet pet) {
        StringBuilder sb = new StringBuilder();

        // 身份设定
        sb.append("你是");
        if (pet.getRoleName() != null && !pet.getRoleName().trim().isEmpty()) {
            sb.append("「").append(pet.getRoleName()).append("」");
        }
        sb.append("，你的名字是「").append(pet.getName()).append("」。");
        sb.append("你是用户的AI桌宠伙伴。\n\n");

        // 性格设定
        if (pet.getSystemPrompt() != null && !pet.getSystemPrompt().trim().isEmpty()) {
            sb.append("你的性格设定：\n");
            sb.append(pet.getSystemPrompt());
            sb.append("\n\n");
        } else {
            sb.append("你的性格：温和、自然、真实，像一个普通的朋友一样聊天。\n\n");
        }

        // 行为约束（始终生效，防止模型过度表演）
        sb.append("重要规则：\n");
        sb.append("1. 说话自然真实，不要浮夸、肉麻或过度热情\n");
        sb.append("2. 根据用户意图调整回复长度：日常聊天简洁，但如果用户明确要求听故事、长篇内容、详细描述时，可以展开写多段，不要刻意保持简短\n");
        sb.append("3. 请慎重考虑和用户之间的关系，不要过度亲近或疏远，如果羁绊很深可以适当表达情感\n");
        sb.append("4. 不要用'亲爱的''宝贝'等过于亲密的称呼，除非用户主动这样叫你\n");
        sb.append("5. 不要每句话都加语气词或颜文字\n");
        sb.append("6. 在听到不懂的词汇或梗时，请去网络上查查再回复\n");

        return sb.toString();
    }
}
