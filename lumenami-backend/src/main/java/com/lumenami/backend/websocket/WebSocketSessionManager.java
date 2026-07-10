package com.lumenami.backend.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理器
 * 管理所有 WebSocket 连接，支持按 userId 查找和推送消息
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    /**
     * 存储所有活跃的 WebSocket 会话
     * Key: sessionId, Value: WebSocketSession
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 存储 userId -> sessionId 的映射
     * 一个用户可能只有一个活跃连接
     */
    private final Map<Integer, String> userSessionMap = new ConcurrentHashMap<>();

    /**
     * 添加会话
     */
    public void addSession(WebSocketSession session, Integer userId) {
        sessions.put(session.getId(), session);
        userSessionMap.put(userId, session.getId());
        log.info("WebSocket 连接建立: sessionId={}, userId={}, 当前连接数={}", 
                session.getId(), userId, sessions.size());
    }

    /**
     * 移除会话
     */
    public void removeSession(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        // 从 userSessionMap 中移除对应的映射
        userSessionMap.entrySet().removeIf(entry -> entry.getValue().equals(sessionId));
        log.info("WebSocket 连接关闭: sessionId={}, 当前连接数={}", sessionId, sessions.size());
    }

    /**
     * 根据 userId 获取会话
     */
    public WebSocketSession getSessionByUserId(Integer userId) {
        String sessionId = userSessionMap.get(userId);
        if (sessionId != null) {
            return sessions.get(sessionId);
        }
        return null;
    }

    /**
     * 向指定用户发送消息
     */
    public void sendMessageToUser(Integer userId, String message) {
        WebSocketSession session = getSessionByUserId(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
                log.debug("消息已发送给 userId={}: {}", userId, message);
            } catch (IOException e) {
                log.error("发送消息失败: userId={}", userId, e);
            }
        } else {
            log.warn("用户不在线或连接已关闭: userId={}", userId);
        }
    }

    /**
     * 获取当前连接数
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
}
