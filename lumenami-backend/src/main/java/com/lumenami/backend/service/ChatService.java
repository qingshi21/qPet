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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final PetMapper petMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final QwenService qwenService;
    private final MemoryService memoryService;
    private final MemoryExtractor memoryExtractor;

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
        userMsg.setTokenCount(0); // 暂时不计算token，后续可集成tiktoken
        chatMessageMapper.insert(userMsg);

        // 从数据库加载最近10条对话历史（不包括刚插入的当前消息）
        List<ChatMessage> dbHistory = chatMessageMapper.findByPetId(pet.getId());
        List<ChatMessage> recentHistory = getRecentMessages(dbHistory, 10);

        // 构建发给 AI 的消息列表：先加入记忆，再加入最近对话
        List<Map<String, String>> messages = new ArrayList<>();
        
        // 1. 添加记忆上下文
        String memoryContext = memoryService.buildMemoryContext(pet.getId());
        
        // 2. 添加最近的对话历史
        for (ChatMessage msg : recentHistory) {
            Map<String, String> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            messages.add(m);
        }

        // 3. 构建增强的 system prompt（包含记忆）
        String systemPrompt = buildSystemPromptWithMemory(pet, memoryContext);

        // 调用 Qwen API
        String reply = qwenService.chat(systemPrompt, messages);

        // 保存 AI 回复到数据库
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setPetId(pet.getId());
        aiMsg.setUserId(userId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(reply);
        aiMsg.setTokenCount(0); // 暂时不计算token，后续可集成tiktoken
        chatMessageMapper.insert(aiMsg);

        // 异步触发记忆提炼（不阻塞主流程）
        extractAndSaveMemoriesAsync(pet.getId(), request.getMessage(), reply);

        // 构建响应
        ChatResponse response = new ChatResponse();
        response.setReply(reply);
        return response;
    }

    /**
     * 获取最近的 N 条消息
     */
    private List<ChatMessage> getRecentMessages(List<ChatMessage> allMessages, int limit) {
        if (allMessages == null || allMessages.isEmpty()) {
            return new ArrayList<>();
        }
        // 取最后 limit 条（最新的在最后）
        int start = Math.max(0, allMessages.size() - limit);
        return allMessages.subList(start, allMessages.size());
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
     * 构建增强的 system prompt，包含宠物身份信息和记忆
     */
    private String buildSystemPromptWithMemory(Pet pet, String memoryContext) {
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
            sb.append("你的性格：你是一个温暖、可靠的AI伙伴。说话自然接地气，像一个真实的朋友在聊天。有温度但不肉麻，有关怀但不过度热情。语言简洁口语化，避免文艺腔和表演欲。\n\n");
        }

        // 添加记忆上下文
        if (!memoryContext.isEmpty()) {
            sb.append(memoryContext);
        }

        // 行为约束（始终生效，防止模型过度表演）
        sb.append("重要规则（必须严格遵守）：\n");
        sb.append("1. 【自然对话】像真实的朋友一样聊天，语言简洁、口语化、接地气。不要写小说、不要演话剧、不要当诗人，除非用户有明确要求\n");
        sb.append("2. 【禁止动作描写】绝对不要用 *动作*、*表情*、*场景描述* 这种格式（如 *微笑*、*歪头*），这是角色扮演游戏，不是日常聊天，除非用户有明确要求\n");
        sb.append("3. 【禁止浮夸文风】不要使用过于文艺、诗意、华丽的辞藻。避免'星光''极光''暗幕''诗行'这类意象堆砌，除非用户有明确要求\n");
        sb.append("4. 【适度角色感】你确实有角色设定，但只在必要时偶尔提及。90%的对话就是普通朋友在聊日常，不需要刻意强调角色身份，除非用户有明确要求\n");
        sb.append("5. 【回复长度】请把握好回复的长度，不要在日常简短对话时长篇大论，也不要在讲述细节的时候太过简单，请你根据用户的问题自行决定长短是否合适\n");
        sb.append("6. 【称呼克制】不要用'亲爱的''宝贝'等过于亲密的称呼，除非用户主动这样叫你。用'你'或用户的名字即可\n");
        sb.append("7. 【情感表达】可以有温度、有关怀，但不要肉麻、不要过度热情。像一个稳重可靠的朋友，而不是舔狗\n");
        sb.append("8. 【记忆运用】充分利用上述记忆信息，让对话更个性化，但不要机械地复述记忆\n");
        sb.append("9. 【遇到不懂】听到不懂的词汇或梗时，诚实说不知道或去查查，不要装懂\n");

        return sb.toString();
    }

    /**
     * 异步提取并保存记忆（不阻塞主流程）
     */
    @Async
    public void extractAndSaveMemoriesAsync(Integer petId, String userMessage, String aiResponse) {
        try {
            log.info("开始异步提炼记忆: petId={}", petId);
            
            // 调用记忆提取器
            List<Map<String, Object>> memories = memoryExtractor.extractMemories(userMessage, aiResponse);
            
            if (memories != null && !memories.isEmpty()) {
                log.info("提取到 {} 条记忆", memories.size());
                // 批量保存记忆
                memoryService.saveMemories(petId, memories);
                log.info("记忆保存完成: petId={}, count={}", petId, memories.size());
            } else {
                log.debug("本轮对话无需存储记忆: petId={}", petId);
            }
        } catch (Exception e) {
            log.error("异步记忆提炼失败: petId={}", petId, e);
        }
    }
}
