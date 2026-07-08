package com.lumenami.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumenami.backend.exception.BusinessException;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class QwenService {

    @Value("${qwen.api.url}")
    private String apiUrl;

    @Value("${qwen.api.key}")
    private String apiKey;

    @Value("${qwen.api.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用 Qwen API 进行对话
     *
     * @param systemPrompt 宠物的性格设定
     * @param history      对话历史
     * @return AI 回复内容
     */
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
