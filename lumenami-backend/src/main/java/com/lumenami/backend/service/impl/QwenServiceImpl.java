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

import java.time.Duration;
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
}
