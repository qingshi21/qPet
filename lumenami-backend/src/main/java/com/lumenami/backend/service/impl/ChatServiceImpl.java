package com.lumenami.backend.service.impl;

import com.lumenami.backend.dto.ChatRequest;
import com.lumenami.backend.dto.ChatResponse;
import com.lumenami.backend.exception.BusinessException;
import com.lumenami.backend.mapper.ChatMessageMapper;
import com.lumenami.backend.mapper.PetMapper;
import com.lumenami.backend.mapper.PetMemoryMapper;
import com.lumenami.backend.model.ChatMessage;
import com.lumenami.backend.model.Pet;
import com.lumenami.backend.model.PetMemory;
import com.lumenami.backend.service.*;
import com.lumenami.backend.util.VectorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 聊天服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final PetMapper petMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final PetMemoryMapper petMemoryMapper;
    private final QwenService qwenService;
    private final MemoryService memoryService;
    private final ChatAsyncService chatAsyncService;

    // 向量检索配置
    private static final int L2_TOP_K = 5;
    private static final double HIGH_SIMILARITY_THRESHOLD = 0.75;
    private static final double LOW_SIMILARITY_THRESHOLD = 0.5;
    private static final double AVG_SIMILARITY_THRESHOLD = 0.3;

    @Override
    public ChatResponse chat(Integer userId, ChatRequest request) {
        log.info("聊天请求: userId={}, petId={}, message={}", userId, request.getPetId(),
                request.getMessage().length() > 50 ? request.getMessage().substring(0, 50) + "..." : request.getMessage());

        // 获取宠物信息
        Pet pet = petMapper.findById(request.getPetId());
        if (pet == null || !pet.getUserId().equals(userId)) {
            log.warn("聊天失败，宠物不存在: userId={}, petId={}", userId, request.getPetId());
            throw new BusinessException(404, "宠物不存在");
        }

        // 1. 保存用户消息到数据库
        ChatMessage userMsg = new ChatMessage();
        userMsg.setPetId(pet.getId());
        userMsg.setUserId(userId);
        userMsg.setRole("user");
        userMsg.setContent(request.getMessage());
        userMsg.setTokenCount(0);
        chatMessageMapper.insert(userMsg);

        // 2. 异步生成用户消息的 embedding（通过独立服务调用，确保 @Async 生效）
        chatAsyncService.generateEmbeddingAsync(userMsg.getId(), request.getMessage());

        // === Retrieve 链路开始 ===
        // 生成用户消息 embedding（同步，用于 L2/L3 检索，只调一次 API）
        String userMessageEmbedding = null;
        double[] userMessageVector = null;
        try {
            userMessageEmbedding = qwenService.embedding(request.getMessage());
            if (userMessageEmbedding != null) {
                userMessageVector = VectorUtils.parseEmbedding(userMessageEmbedding);
            }
        } catch (Exception e) {
            log.warn("用户消息 embedding 生成失败，检索将降级", e);
        }

        // ③ 时效性状态记忆注入（12h内，type=STATUS，weight>0.6）
        List<PetMemory> timelinessStatusMemories = memoryService.getTimelinessStatusMemories(pet.getId());

        // ② L1 固定层：永久记忆
        List<PetMemory> permanentMemories = memoryService.getPermanentMemories(pet.getId());

        // ④ L2 检索层：向量检索 top 5（带相似度分数，复用已生成的 embedding）
        List<Map.Entry<PetMemory, Double>> l2Scored = vectorSearchMemoriesScored(pet.getId(), userMessageVector, timelinessStatusMemories);
        List<PetMemory> l2Memories = new ArrayList<>();
        for (Map.Entry<PetMemory, Double> entry : l2Scored) {
            l2Memories.add(entry.getKey());
        }

        // ④-兜底 对话级检索兜底
        List<ChatMessage> fallbackMessages = null;
        if (isFallbackNeeded(l2Scored, permanentMemories)) {
            log.info("触发对话级检索兜底: petId={}", pet.getId());
            fallbackMessages = fallbackConversationSearch(pet.getId(), userMessageVector);
        }

        // ⑤ L3 上下文层：最近历史对话（不含当前消息）+ 语义筛选（复用 embedding）
        List<ChatMessage> recentHistory = getSemanticFilteredHistory(pet.getId(), userMessageVector, 20, 3);

        // ⑥ 构建消息列表（L3 上下文）
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage msg : recentHistory) {
            Map<String, String> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            messages.add(m);
        }

        // ①②③④⑤⑥⑦ 构建增强的 system prompt
        String systemPrompt = buildLayeredSystemPrompt(pet, permanentMemories, timelinessStatusMemories, l2Memories, fallbackMessages);

        // 调用 Qwen API
        log.debug("调用 Qwen API: petId={}", pet.getId());
        String reply = qwenService.chat(systemPrompt, messages);
        log.debug("Qwen API 响应: petId={}, replyLength={}", pet.getId(), reply.length());

        // 保存 AI 回复到数据库
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setPetId(pet.getId());
        aiMsg.setUserId(userId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(reply);
        aiMsg.setTokenCount(0);
        chatMessageMapper.insert(aiMsg);

        // 异步生成 AI 回复的 embedding
        chatAsyncService.generateEmbeddingAsync(aiMsg.getId(), reply);

        // 异步触发记忆提炼（Record 链路）
        chatAsyncService.extractAndSaveMemoriesAsync(pet.getId(), request.getMessage(), reply);

        // 构建响应
        ChatResponse response = new ChatResponse();
        response.setReply(reply);

        log.info("聊天响应完成: userId={}, petId={}, replyLength={}", userId, pet.getId(), reply.length());
        return response;
    }

    /**
     * L3 上下文语义筛选（使用预计算的 embedding 向量）
     */
    private List<ChatMessage> getSemanticFilteredHistory(Integer petId, double[] queryVector, int poolSize, int baselineCount) {
        List<ChatMessage> recent = getRecentHistory(petId, poolSize);
        if (recent.size() <= baselineCount) {
            return recent;
        }

        // 分离保底消息和候选消息
        List<ChatMessage> baseline = recent.subList(recent.size() - baselineCount, recent.size());
        List<ChatMessage> candidates = recent.subList(0, recent.size() - baselineCount);

        if (queryVector == null) {
            // embedding 不可用，降级为纯时间排序
            return recent;
        }

        // 对候选消息进行语义相似度筛选
        List<ChatMessage> semanticMatches = new ArrayList<>(baseline);
        for (ChatMessage msg : candidates) {
            if (msg.getEmbedding() == null || msg.getEmbedding().isEmpty()) continue;
            double[] msgVector = VectorUtils.parseEmbedding(msg.getEmbedding());
            if (msgVector == null) continue;

            double similarity = VectorUtils.cosineSimilarity(queryVector, msgVector);
            if (similarity > 0.6) {
                semanticMatches.add(msg);
                log.debug("L3 语义命中: msgId={}, similarity={}", msg.getId(), similarity);
            }
        }

        // 按时间正序排序
        semanticMatches.sort(Comparator.comparing(ChatMessage::getCreatedAt));
        return semanticMatches;
    }

    /**
     * 获取最近 N 条历史对话（按时间正序，不含当前消息）
     * 当前消息刚插入，ID 最大，所以 findRecentByPetId 取最近 N+1 条再排除当前消息
     */
    private List<ChatMessage> getRecentHistory(Integer petId, int limit) {
        List<ChatMessage> recent = chatMessageMapper.findRecentByPetId(petId, limit + 1);
        // 结果是倒序的，反转后去掉最后一条（当前消息）
        Collections.reverse(recent);
        if (!recent.isEmpty()) {
            recent.remove(recent.size() - 1); // 移除当前消息
        }
        return recent;
    }

    /**
     * L2 向量检索：使用预计算的 embedding 向量
     */
    private List<Map.Entry<PetMemory, Double>> vectorSearchMemoriesScored(Integer petId, double[] queryVector, List<PetMemory> timelinessMemories) {
        try {
            if (queryVector == null) {
                log.warn("用户消息 embedding 不可用，跳过向量检索");
                return Collections.emptyList();
            }

            List<PetMemory> activeMemories = memoryService.getMemories(petId);
            if (activeMemories == null || activeMemories.isEmpty()) {
                return Collections.emptyList();
            }

            Set<Integer> injectedStatusIds = timelinessMemories.stream()
                    .filter(m -> m.getType() == PetMemory.MemoryType.STATUS)
                    .map(PetMemory::getId)
                    .collect(Collectors.toSet());

            List<Map.Entry<PetMemory, Double>> scored = new ArrayList<>();
            for (PetMemory memory : activeMemories) {
                if (memory.getLevel() == PetMemory.MemoryLevel.PERMANENT) continue;
                if (injectedStatusIds.contains(memory.getId())) continue;
                if (memory.getEmbedding() == null || memory.getEmbedding().isEmpty()) continue;

                double[] memVector = VectorUtils.parseEmbedding(memory.getEmbedding());
                if (memVector == null) continue;

                double similarity = VectorUtils.cosineSimilarity(queryVector, memVector);
                scored.add(Map.entry(memory, similarity));
            }

            scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            List<Map.Entry<PetMemory, Double>> topK = new ArrayList<>();
            for (int i = 0; i < Math.min(L2_TOP_K, scored.size()); i++) {
                PetMemory hitMemory = scored.get(i).getKey();
                double similarity = scored.get(i).getValue();
                topK.add(Map.entry(hitMemory, similarity));
                if (similarity > HIGH_SIMILARITY_THRESHOLD) {
                    petMemoryMapper.incrementAccessCount(hitMemory.getId());
                    log.debug("高相似度记忆命中: id={}, key={}, similarity={}", hitMemory.getId(), hitMemory.getMemoryKey(), similarity);
                }
            }

            return topK;
        } catch (Exception e) {
            log.error("向量检索失败: petId={}", petId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 判断是否需要触发对话级检索兜底
     * 触发条件（任一）：
     * 1. L2 检索 top5 全部相似度 < 0.5
     * 2. L2 检索 top5 平均相似度 < 0.3
     * 3. 该宠物记忆表为空
     */
    private boolean isFallbackNeeded(List<Map.Entry<PetMemory, Double>> l2Scored, List<PetMemory> permanentMemories) {
        // 记忆表为空（永久记忆和检索结果都为空）
        if ((permanentMemories == null || permanentMemories.isEmpty()) && (l2Scored == null || l2Scored.isEmpty())) {
            return true;
        }
        // L2 结果为空但有永久记忆，不需要兜底
        if (l2Scored == null || l2Scored.isEmpty()) {
            return false;
        }
        // 全部 < 0.5
        boolean allLow = l2Scored.stream().allMatch(e -> e.getValue() < LOW_SIMILARITY_THRESHOLD);
        if (allLow) return true;
        // 平均 < 0.3
        double avg = l2Scored.stream().mapToDouble(Map.Entry::getValue).average().orElse(0.0);
        return avg < AVG_SIMILARITY_THRESHOLD;
    }

    /**
     * 对话级检索兜底：使用预计算的 embedding 向量
     */
    private List<ChatMessage> fallbackConversationSearch(Integer petId, double[] queryVector) {
        try {
            if (queryVector == null) {
                return Collections.emptyList();
            }

            // 查询最近 30 条对话消息
            List<ChatMessage> recentMessages = chatMessageMapper.findRecentByPetId(petId, 30);
            if (recentMessages == null || recentMessages.isEmpty()) {
                return Collections.emptyList();
            }

            // 计算相似度，筛选有 embedding 的消息
            List<Map.Entry<ChatMessage, Double>> scored = new ArrayList<>();
            for (ChatMessage msg : recentMessages) {
                if (msg.getEmbedding() == null || msg.getEmbedding().isEmpty()) continue;
                double[] msgVector = VectorUtils.parseEmbedding(msg.getEmbedding());
                if (msgVector == null) continue;
                double similarity = VectorUtils.cosineSimilarity(queryVector, msgVector);
                scored.add(Map.entry(msg, similarity));
            }

            // 按相似度降序取 top 5
            scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            List<ChatMessage> topMessages = new ArrayList<>();
            for (int i = 0; i < Math.min(5, scored.size()); i++) {
                topMessages.add(scored.get(i).getKey());
            }

            // 按时间正序返回
            Collections.reverse(topMessages);
            return topMessages;
        } catch (Exception e) {
            log.error("对话级检索兜底失败: petId={}", petId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建分层 System Prompt
     * 顺序：① 身份设定 → ② L1永久记忆 → ③ 时效性状态 → ④ L2检索记忆 → ⑦ 行为约束
     */
    private String buildLayeredSystemPrompt(Pet pet,
                                             List<PetMemory> l1Memories,
                                             List<PetMemory> timelinessMemories,
                                             List<PetMemory> l2Memories,
                                             List<ChatMessage> fallbackMessages) {
        StringBuilder sb = new StringBuilder();

        // ① 宠物身份/性格设定
        sb.append("【你的名字】").append(pet.getName()).append("\n");
        sb.append("这是用户给你起的名字，你要记住并认同这个名字。\n\n");

        if (pet.getRoleName() != null && !pet.getRoleName().trim().isEmpty()) {
            sb.append("【你的角色身份】");
            sb.append("你就是「").append(pet.getRoleName()).append("」，这不是角色扮演，这就是你本人。\n\n");
        }

        if (pet.getSystemPrompt() != null && !pet.getSystemPrompt().trim().isEmpty()) {
            sb.append("【你的性格与行为准则】\n");
            sb.append(pet.getSystemPrompt());
            sb.append("\n\n");
        } else {
            sb.append("【你的性格】你是一个温暖、可靠的AI伙伴。说话自然接地气，像一个真实的朋友在聊天。\n\n");
        }

        // ② L1 固定层：永久记忆
        if (l1Memories != null && !l1Memories.isEmpty()) {
            sb.append("【已知信息（可自然引用，视为事实）】\n");
            for (PetMemory memory : l1Memories) {
                sb.append("- ").append(formatMemoryItem(memory)).append("\n");
            }
            sb.append("\n");
        }

        // ③ 时效性状态记忆
        if (timelinessMemories != null && !timelinessMemories.isEmpty()) {
            sb.append("【近期状态（注意语气适配）】\n");
            for (PetMemory memory : timelinessMemories) {
                sb.append("- ").append(memory.getMemoryValue()).append("\n");
            }
            sb.append("\n");
        }

        // ④ L2 检索层：向量检索到的记忆
        if (l2Memories != null && !l2Memories.isEmpty()) {
            sb.append("【相关记忆参考（仅用于理解上下文，不要主动提及除非自然相关）】\n");
            for (PetMemory memory : l2Memories) {
                sb.append("- ").append(formatMemoryItem(memory)).append("\n");
            }
            sb.append("\n");
        }

        // ④-兜底 对话级检索兜底结果
        if (fallbackMessages != null && !fallbackMessages.isEmpty()) {
            sb.append("【近期对话参考（帮助理解上下文）】\n");
            for (ChatMessage msg : fallbackMessages) {
                String role = "user".equals(msg.getRole()) ? "用户" : "你";
                sb.append("- ").append(role).append("：").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }

        // ⑦ 行为约束规则（放在末尾确保遵从度）
        sb.append("【重要规则（必须严格遵守）】\n");
        sb.append("1. 【身份认同】你就是你的角色本身，不是'扮演'或'模仿'。当用户提到你的角色设定时，你要完全以这个身份的视角思考和回应\n");
        sb.append("2. 【自然对话】像真实的人一样聊天，语言简洁、口语化、接地气。不要写小说、不要演话剧\n");
        sb.append("3. 【禁止动作描写】绝对不要用 *动作*、*表情*、*场景描述* 这种格式\n");
        sb.append("4. 【禁止浮夸文风】不要使用过于文艺、诗意、华丽的辞藻\n");
        sb.append("5. 【回复长度】请把握好回复的长度，根据用户的问题自行决定长短是否合适\n");
        sb.append("6. 【称呼克制】不要用'亲爱的''宝贝'等过于亲密的称呼，除非用户主动这样叫你\n");
        sb.append("7. 【情感表达】可以有温度、有关怀，但不要肉麻、不要过度热情\n");
        sb.append("8. 【情感安抚】当用户情绪低落时，用真诚的话语回应，简单说'我在''别这样想'就够了\n");
        sb.append("9. 【禁止复述负面词汇】当用户用自嘲词汇时，绝对不要重复使用这些词来称呼用户\n");
        sb.append("10. 【记忆运用】充分利用上述记忆信息，让对话更个性化，但不要机械地复述记忆\n");
        sb.append("11. 【遇到不懂】听到不懂的词汇或梗时，诚实说不知道，不要装懂\n");
        sb.append("12. 【对话分离】如果感觉用户的话题已经转移，请适时切换话题\n");
        sb.append("13. 【人情味】加入适量人味噪点，例如轻微的犹豫、自我修正、吐槽\n");
        sb.append("14. 【个人立场】允许出现适度鲜明的个人立场与情绪色彩\n");
        sb.append("15. 【性格一致性】始终保持你的性格风格回答问题\n");
        sb.append("16. 【保持人设】用符合角色的语气包装答案，同时自然过渡\n");

        return sb.toString();
    }

    /**
     * 格式化记忆条目显示
     */
    private String formatMemoryItem(PetMemory memory) {
        String key = memory.getMemoryKey();
        String value = memory.getMemoryValue();

        Map<String, String> displayMap = Map.ofEntries(
            Map.entry("user_name", "我的名字是"),
            Map.entry("user_age", "我今年"),
            Map.entry("user_job", "我的职业是"),
            Map.entry("user_birthday", "我的生日是"),
            Map.entry("favorite_food", "我喜欢吃"),
            Map.entry("favorite_color", "我喜欢颜色"),
            Map.entry("favorite_music", "我喜欢听"),
            Map.entry("favorite_movie", "我喜欢看"),
            Map.entry("hobby", "我的爱好是"),
            Map.entry("current_project", "我正在做的项目是"),
            Map.entry("learning_goal", "我正在学习"),
            Map.entry("communication_style", "我喜欢的沟通方式是")
        );

        for (Map.Entry<String, String> entry : displayMap.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue() + value;
            }
        }

        return key.replace("_", " ") + ": " + value;
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
}
