package com.lumenami.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumenami.backend.service.MemoryExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 记忆提取服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryExtractorImpl implements MemoryExtractor {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${qwen.api.url}")
    private String apiUrl;

    @Value("${qwen.api.key}")
    private String apiKey;

    @Value("${qwen.api.model:qwen-plus}")
    private String model;

    @Override
    public List<Map<String, Object>> extractMemories(String userMessage, String aiResponse) {
        try {
            String systemPrompt = buildExtractionPrompt();

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content",
                String.format("用户消息：%s\n\nAI回复：%s", userMessage, aiResponse)));

            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 500);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.POST, entity, String.class);

            JsonNode rootNode = objectMapper.readTree(response.getBody());
            String content = rootNode.path("choices").get(0).path("message").path("content").asText();

            log.info("Qwen 记忆提取原始返回: {}", content);

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

            需要提取的信息类型（type 字段）：
            1. **PROFILE**（用户画像）：用户的姓名、年龄、职业、城市、兴趣爱好等个人信息
            2. **PROJECT**（项目信息）：用户正在做的项目、技术栈、工作内容、学习目标等
            3. **PREFERENCE**（偏好）：用户喜欢的沟通方式、话题偏好、学习风格等
            4. **STATUS**（用户状态）：用户当前的心情、情绪状态、近期感受等（如"今天心情不好""最近压力很大""好开心"）

            记忆级别（level 字段）：
            - **PERMANENT**：核心身份信息，如姓名、职业、年龄等不会轻易改变的信息
            - **LONG_TERM**：重要但可能随时间变化的信息，如在做的项目、兴趣爱好、重要经历
            - **SHORT_TERM**：临时性信息，如当天心情、临时兴趣、突发事件、近期状态

            提取规则：
            - 只提取用户主动陈述的、明确的、有价值的事实性信息
            - 忽略闲聊、问候、无关内容
            - 忽略用户对 AI 的猜测、评价、提问
            - 忽略 AI 的角色扮演内容、虚构设定、玩笑话
            - 如果对话中没有值得记忆的信息，返回空数组 []
            - 每条记忆的 key 应该简洁明了（如：user_name, favorite_language, current_project）
            - 每条记忆的 value 应该是完整的事实描述
            - importance 评分范围：0.0-1.0（0.1-0.3 普通信息，0.4-0.6 重要信息，0.7-1.0 关键信息）

            输出格式（必须是合法的 JSON 数组，不要使用 markdown 代码块包裹，直接输出纯 JSON）：
            [
              {
                "key": "记忆键名",
                "value": "记忆值",
                "type": "PROFILE|PROJECT|PREFERENCE|STATUS",
                "level": "PERMANENT|LONG_TERM|SHORT_TERM",
                "importance": 0.0-1.0
              }
            ]

            ✅ 正确示例1（用户明确陈述自己的信息）：
            用户："我叫小明，是一名Java开发工程师"
            AI："你好小明！很高兴认识你..."
            输出：[{"key": "user_name", "value": "小明", "type": "PROFILE", "level": "PERMANENT", "importance": 0.8}]

            ✅ 正确示例2（用户陈述自己的项目）：
            用户："我正在用Spring Boot开发一个电商系统"
            AI："听起来很棒..."
            输出：[{"key": "current_project", "value": "使用Spring Boot开发电商系统", "type": "PROJECT", "level": "LONG_TERM", "importance": 0.7}]

            ✅ 正确示例3（用户表达心情状态）：
            用户："今天被老板骂了，心情很差"
            AI："别太在意..."
            输出：[{"key": "mood_today", "value": "今天被老板骂了，心情很差", "type": "STATUS", "level": "SHORT_TERM", "importance": 0.7}]

            ❌ 错误示例（AI 的角色扮演内容，不能提取）：
            用户："你说话像重庆口音"
            AI："哈哈，其实我是重庆人"
            输出：[]

            现在请分析以下对话，提取需要记忆的信息：
            """;
    }

    /**
     * 解析记忆JSON（支持 markdown 代码块包裹的格式）
     */
    private List<Map<String, Object>> parseMemories(String jsonContent) {
        try {
            // 去除 markdown 代码块包裹（如 ```json [...] ``` 或 ``` [...] ```）
            String cleaned = jsonContent.trim();
            if (cleaned.startsWith("```")) {
                // 移除开头的 ```json 或 ```
                int firstNewline = cleaned.indexOf('\n');
                if (firstNewline > 0) {
                    cleaned = cleaned.substring(firstNewline + 1);
                }
                // 移除结尾的 ```
                if (cleaned.endsWith("```")) {
                    cleaned = cleaned.substring(0, cleaned.length() - 3);
                }
                cleaned = cleaned.trim();
            }

            JsonNode node = objectMapper.readTree(cleaned);
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
                    memory.put("level", memoryNode.has("level") ? memoryNode.get("level").asText() : "LONG_TERM");
                    memory.put("importance", memoryNode.has("importance") ?
                        memoryNode.get("importance").asDouble() : 0.5);
                    memories.add(memory);
                }
            }
            return memories;
        } catch (Exception e) {
            log.warn("解析记忆JSON失败, 原始内容: {}", jsonContent, e);
            return Collections.emptyList();
        }
    }
}
