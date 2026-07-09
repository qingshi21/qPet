package com.lumenami.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryExtractor {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${qwen.api.url}")
    private String apiUrl;

    @Value("${qwen.api.key}")
    private String apiKey;

    @Value("${qwen.api.model:qwen-plus}")
    private String model;

    /**
     * 从对话中提炼记忆
     * @param userMessage 用户消息
     * @param aiResponse AI回复
     * @return 提取的记忆列表，如果没有需要存储的记忆则返回空列表
     */
    public List<Map<String, Object>> extractMemories(String userMessage, String aiResponse) {
        try {
            // 构建提示词
            String systemPrompt = buildExtractionPrompt();
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", 
                String.format("用户消息：%s\n\nAI回复：%s", userMessage, aiResponse)));
            
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 500);

            // 调用 Qwen API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.POST, entity, String.class);

            // 解析响应
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            String content = rootNode.path("choices").get(0).path("message").path("content").asText();
            
            // 解析 JSON 格式的记忆数据
            return parseMemories(content);
            
        } catch (Exception e) {
            log.error("记忆提炼失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建记忆提取的 System Prompt
     */
    private String buildExtractionPrompt() {
        return """
            你是一个智能记忆提取助手。请分析用户和AI的对话，判断是否需要从中提取重要信息作为长期记忆。
                
            ⚠️ 核心原则（必须严格遵守）：
            - **只提取用户明确陈述的关于自己的信息**
            - **绝对不要提取 AI 说的话作为用户记忆**
            - **如果信息来自 AI 的角色扮演、虚构设定、玩笑话，一律不要提取**
            - 例如：AI说“我是重庆人”是角色设定，不能提取成用户的 location；用户说“你说话像重庆人”只是猜测，也不能提取
                
            需要提取的信息类型：
            1. **profile**（用户画像）：用户的姓名、年龄、职业、城市、兴趣爱好等个人信息
            2. **project**（项目信息）：用户正在做的项目、技术栈、工作内容、学习目标等
            3. **preference**（偏好）：用户喜欢的沟通方式、话题偏好、学习风格等
                
            提取规则：
            - 只提取用户主动陈述的、明确的、有价值的事实性信息
            - 忽略闲聊、问候、无关内容
            - 忽略用户对 AI 的猜测、评价、提问（如“你是哪里的？”“你说话像重庆人”）
            - 忽略 AI 的角色扮演内容、虚构设定、玩笑话
            - 如果对话中没有值得记忆的信息，返回空数组 []
            - 每条记忆的 key 应该简洁明了（如：user_name, favorite_language, current_project）
            - 每条记忆的 value 应该是完整的事实描述
            - importance 评分标准：1-3（普通信息），4-6（重要信息），7-10（关键信息）
                
            输出格式（必须是合法的 JSON 数组）：
            [
              {
                "key": "记忆键名",
                "value": "记忆值",
                "type": "PROFILE|PROJECT|PREFERENCE",
                "importance": 1-10
              }
            ]
                
            ✅ 正确示例1（用户明确陈述自己的信息）：
            用户：“我叫小明，是一名Java开发工程师”
            AI：“你好小明！很高兴认识你...”
            输出：[{"key": "user_name", "value": "小明", "type": "PROFILE", "importance": 8}]
                
            ✅ 正确示例2（用户陈述自己的项目）：
            用户：“我正在用Spring Boot开发一个电商系统，遇到了数据库性能问题”
            AI：“可以考虑使用缓存或者优化SQL...”
            输出：[{"key": "current_project", "value": "使用Spring Boot开发电商系统", "type": "PROJECT", "importance": 7}]
                
            ❌ 错误示例1（AI 的角色扮演内容，不能提取）：
            用户：“你说话像重庆口音”
            AI：“哈哈，其实我是重庆人，从小在那儿长大~”
            输出：[]  （这是 AI 的角色设定，不是用户的信息）
                
             错误示例2（用户对 AI 的猜测，不能提取）：
            用户：“你说话像重庆人”
            AI：“是吗？可能是最近吃火锅多了”
            输出：[]
                
            现在请分析以下对话，提取需要记忆的信息：
            """;
    }

    /**
     * 解析记忆JSON
     */
    private List<Map<String, Object>> parseMemories(String jsonContent) {
        try {
            // 尝试直接解析为数组
            JsonNode node = objectMapper.readTree(jsonContent);
            if (!node.isArray()) {
                return Collections.emptyList();
            }
            
            List<Map<String, Object>> memories = new ArrayList<>();
            for (JsonNode memoryNode : node) {
                if (memoryNode.has("key") && memoryNode.has("value") && memoryNode.has("type")) {
                    Map<String, Object> memory = new HashMap<>();
                    memory.put("key", memoryNode.get("key").asText());
                    memory.put("value", memoryNode.get("value").asText());
                    memory.put("type", memoryNode.get("type").asText());
                    memory.put("importance", memoryNode.has("importance") ? 
                        memoryNode.get("importance").asInt() : 5);
                    memories.add(memory);
                }
            }
            return memories;
        } catch (Exception e) {
            log.warn("解析记忆JSON失败: {}", jsonContent, e);
            return Collections.emptyList();
        }
    }
}
