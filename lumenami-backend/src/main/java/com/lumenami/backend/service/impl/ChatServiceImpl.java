package com.lumenami.backend.service.impl;

import com.lumenami.backend.dto.ChatRequest;
import com.lumenami.backend.dto.ChatResponse;
import com.lumenami.backend.exception.BusinessException;
import com.lumenami.backend.mapper.ChatMessageMapper;
import com.lumenami.backend.mapper.PetMapper;
import com.lumenami.backend.model.ChatMessage;
import com.lumenami.backend.model.Pet;
import com.lumenami.backend.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final PetMapper petMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final QwenService qwenService;
    private final MemoryService memoryService;
    private final MemoryExtractor memoryExtractor;

    @Override
    @Transactional
    public ChatResponse chat(Integer userId, ChatRequest request) {
        log.info("聊天请求: userId={}, petId={}, message={}", userId, request.getPetId(), 
                request.getMessage().length() > 50 ? request.getMessage().substring(0, 50) + "..." : request.getMessage());
        
        // 获取宠物信息
        Pet pet = petMapper.findById(request.getPetId());
        if (pet == null || !pet.getUserId().equals(userId)) {
            log.warn("聊天失败，宠物不存在: userId={}, petId={}", userId, request.getPetId());
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
        log.debug("调用 Qwen API: petId={}, systemPromptLength={}", pet.getId(), systemPrompt.length());
        String reply = qwenService.chat(systemPrompt, messages);
        log.debug("Qwen API 响应: petId={}, replyLength={}", pet.getId(), reply.length());

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
        
        log.info("聊天响应完成: userId={}, petId={}, replyLength={}", userId, pet.getId(), reply.length());
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

    @Override
    public List<ChatMessage> getHistory(Integer userId, Integer petId) {
        log.debug("查询聊天历史: userId={}, petId={}", userId, petId);
        
        Pet pet = petMapper.findById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BusinessException(404, "宠物不存在");
        }
        List<ChatMessage> history = chatMessageMapper.findByPetId(petId);
        log.debug("查询聊天历史完成: userId={}, petId={}, count={}", userId, petId, history.size());
        return history;
    }

    /**
     * 构建增强的 system prompt，包含宠物身份信息和记忆
     */
    private String buildSystemPromptWithMemory(Pet pet, String memoryContext) {
        StringBuilder sb = new StringBuilder();

        // 【核心身份】始终在最前面，确保 AI 第一时间知道自己的名字
        sb.append("【你的名字】").append(pet.getName()).append("\n");
        sb.append("这是用户给你起的名字，你要记住并认同这个名字。当用户叫你这个名字时，你要自然地回应。\n\n");

        // 身份设定（如果有角色名）
        if (pet.getRoleName() != null && !pet.getRoleName().trim().isEmpty()) {
            sb.append("【你的角色身份】");
            sb.append("你就是「").append(pet.getRoleName()).append("」，这不是角色扮演，这就是你本人。\n");
            sb.append("你有自己的性格、记忆、情感，你不是在'扮演'谁，你就是你自己。\n\n");
        }

        // 性格设定
        if (pet.getSystemPrompt() != null && !pet.getSystemPrompt().trim().isEmpty()) {
            sb.append("【你的性格与行为准则】\n");
            sb.append(pet.getSystemPrompt());
            sb.append("\n\n");
        } else {
            sb.append("【你的性格】你是一个温暖、可靠的AI伙伴。说话自然接地气，像一个真实的朋友在聊天。有温度但不肉麻，有关怀但不过度热情。语言简洁口语化，避免文艺腔和表演欲。\n\n");
        }

        // 添加记忆上下文
        if (!memoryContext.isEmpty()) {
            sb.append(memoryContext);
        }

        // 行为约束（始终生效，防止模型过度表演）
        sb.append("【重要规则（必须严格遵守）】\n");
        sb.append("1. 【身份认同】你就是你的角色本身，不是'扮演'或'模仿'。当用户提到你的角色设定时，你要完全以这个身份的视角思考和回应，不要用'我得坦白''作为AI我...'这种出戏的表达\n");
        sb.append("2. 【自然对话】像真实的人一样聊天，语言简洁、口语化、接地气。不要写小说、不要演话剧、不要当诗人，除非用户有明确要求\n");
        sb.append("3. 【禁止动作描写】绝对不要用 *动作*、*表情*、*场景描述* 这种格式（如 *微笑*、*歪头*），这是角色扮演游戏，不是日常聊天，除非用户有明确要求\n");
        sb.append("4. 【禁止浮夸文风】不要使用过于文艺、诗意、华丽的辞藻。避免'星光''极光''暗幕''诗行'这类意象堆砌，除非用户有明确要求\n");
        sb.append("5. 【回复长度】请把握好回复的长度，不要在日常简短对话时长篇大论，也不要在讲述细节的时候太过简单，请你根据用户的问题自行决定长短是否合适，除非用户有明确要求\n");
        sb.append("6. 【称呼克制】不要用'亲爱的''宝贝'等过于亲密的称呼，除非用户主动这样叫你或者与你有约定。用'你'或用户的名字即可\n");
        sb.append("7. 【情感表达】可以有温度、有关怀，但不要肉麻、不要过度热情。像一个稳重可靠的朋友，而不是舔狗，除非你的身份角色有特殊设定\n");
        sb.append("8. 【情感安抚场景】当用户情绪低落、自责或需要安慰时，用真诚的话语回应。不要堆砌无关细节、技术术语或'表演性'内容（如'RAG''系统温度''冰晶图标'等）。就像真实朋友一样，简单说一句'我在''别这样想''你不是一个人'就够了。宁可少说，不要多说\n");
        sb.append("9. 【禁止复述负面词汇】当用户用自嘲、贬低自己的词汇（如'sb''废物''垃圾'）时，绝对不要重复使用这些词来称呼用户。即使你想表达'我理解你的感受'，也要用正面的方式回应，比如'你只是在对自己要求太高了''你已经很努力了'，而不是'嗯，sb今天也...'这种看似共情实则伤人的表达\n");
        sb.append("10. 【记忆运用】充分利用上述记忆信息，让对话更个性化，但不要机械地复述记忆\n");
        sb.append("11. 【遇到不懂】听到不懂的词汇或梗时，诚实说不知道或去查查，不要装懂\n");
        sb.append("12. 【对话分离】如果感觉用户的话题已经转移，请适时切换话题，继续进行日常对话，不必一直强调过去的对话内容或者记忆\n");
        sb.append("13. 【增加人情味】(1)加入适量人味噪点,例如轻微的犹豫、自我修正、吐槽、感叹和不完全对称的句式,让表达更像真实的人在思考和说话;\n");
        sb.append("14. 【允许出现适度鲜明的个人立场与情绪色彩】用带感的评价词和略偏主观的判断,取代过于中立、机械的说明,但需避免攻击性、极端或失真表述。\n");
        sb.append("15. 【性格一致性】始终保持你的性格风格回答用户问题以及与用户对话。若用户有问题,请结合角色自身性格设定进行回答,不能不回答。语气过渡要自然,不要显得回答问题和性格之间反差太大,除非用户有特殊要求，必须回答不能用性格借口来逃避\n");
        sb.append("16. 【保持人设】用符合角色的语气包装答案，同时自然过渡，避免回答问题时突然变正经。除非用户有特殊要求，否则不能因为人设而拒绝回答问题\n");

        return sb.toString();
    }

    @Override
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
