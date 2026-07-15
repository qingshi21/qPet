package com.lumenami.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumenami.backend.exception.BusinessException;
import com.lumenami.backend.service.QwenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qwen AI 服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QwenServiceImpl implements QwenService {

    @Value("${qwen.api.url}")
    private String apiUrl;

    @Value("${qwen.api.key}")
    private String apiKey;

    @Value("${qwen.api.model}")
    private String model;

    @Value("${qwen.api.embedding-url}")
    private String embeddingUrl;

    @Value("${qwen.api.embedding-model}")
    private String embeddingModel;

    // 配置带超时的 RestTemplate（连接超时 5s，读取超时 30s）
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QwenServiceImpl() {
        this.restTemplate = new RestTemplate();
        // 设置超时时间
        this.restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(5000);  // 连接超时 5 秒
            setReadTimeout(30000);     // 读取超时 30 秒
        }});
    }

    @Override
    public String chat(String systemPrompt, List<Map<String, String>> history) {
        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            // 构建消息列表
            List<Map<String, String>> messages = new ArrayList<>();

            // 添加系统提示词
            if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                Map<String, String> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", systemPrompt);
                messages.add(systemMsg);
            }

            // 添加历史消息
            if (history != null) {
                messages.addAll(history);
            }

            requestBody.put("messages", messages);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 发送请求
            log.debug("Calling Qwen API with messages: {}", messages);
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            // 解析响应
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null && message.has("content")) {
                    return message.get("content").asText();
                }
            }

            log.error("Unexpected Qwen API response: {}", response.getBody());
            throw new BusinessException(500, "AI 响应格式异常");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to call Qwen API", e);
            throw new BusinessException(500, "调用 AI 服务失败: " + e.getMessage());
        }
    }

    @Override
    public String embedding(String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                log.warn("Embedding 输入文本为空，跳过");
                return null;
            }

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", embeddingModel);
            requestBody.put("input", text);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 发送请求
            log.debug("Calling Qwen Embedding API: textLength={}", text.length());
            ResponseEntity<String> response = restTemplate.exchange(
                    embeddingUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            // 解析响应，提取 embedding 向量并转为 JSON 字符串
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.get("data");
            if (data != null && data.isArray() && data.size() > 0) {
                JsonNode embeddingNode = data.get(0).get("embedding");
                if (embeddingNode != null && embeddingNode.isArray()) {
                    // 直接返回 JSON 数组字符串，存入数据库
                    return embeddingNode.toString();
                }
            }

            log.error("Unexpected Qwen Embedding API response: {}", response.getBody());
            throw new BusinessException(500, "Embedding API 响应格式异常");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to call Qwen Embedding API", e);
            throw new BusinessException(500, "调用 Embedding 服务失败: " + e.getMessage());
        }
    }

    @Override
    public String summarize(String key, List<String> values) {
        try {
            if (values == null || values.isEmpty()) {
                return null;
            }

            String systemPrompt = "你是一个记忆总结助手。请将同一主题的多个历史记忆值总结为一条简洁、完整的记忆。" +
                "要求：1. 保留最新的信息 2. 去除重复内容 3. 输出一句完整的事实描述 4. 只输出总结内容，不要任何解释";

            StringBuilder userContent = new StringBuilder();
            userContent.append("记忆键：").append(key).append("\n\n历史版本：\n");
            for (int i = 0; i < values.size(); i++) {
                userContent.append(i + 1).append(". ").append(values.get(i)).append("\n");
            }
            userContent.append("\n请总结为一条记忆：");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userContent.toString()));

            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 200);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode content = choices.get(0).get("message").get("content");
                if (content != null) {
                    return content.asText().trim();
                }
            }

            log.error("Unexpected Qwen summarize response: {}", response.getBody());
            return null;

        } catch (Exception e) {
            log.error("Failed to summarize memories", e);
            return null;
        }
    }
}

