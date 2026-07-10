package com.lumenami.backend.websocket;

import lombok.Data;

/**
 * WebSocket 消息基类
 */
@Data
public class WebSocketMessage {
    /**
     * 消息类型
     * - chat: 聊天消息
     * - chat_reply: AI 回复
     * - ping: 心跳请求
     * - pong: 心跳响应
     * - error: 错误消息
     * - connected: 连接成功确认
     * - typing: AI 正在输入
     */
    private String type;

    /**
     * 用户ID（连接时传递）
     */
    private Integer userId;

    /**
     * 宠物ID
     */
    private Integer petId;

    /**
     * 消息内容
     */
    private String message;

    /**
     * AI 回复内容
     */
    private String reply;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 创建连接成功消息
     */
    public static WebSocketMessage connected(String message) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType("connected");
        msg.setMessage(message);
        msg.setTimestamp(System.currentTimeMillis());
        return msg;
    }

    /**
     * 创建心跳响应消息
     */
    public static WebSocketMessage pong() {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType("pong");
        msg.setTimestamp(System.currentTimeMillis());
        return msg;
    }

    /**
     * 创建错误消息
     */
    public static WebSocketMessage error(String errorMessage) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType("error");
        msg.setErrorMessage(errorMessage);
        msg.setTimestamp(System.currentTimeMillis());
        return msg;
    }

    /**
     * 创建 AI 回复消息
     */
    public static WebSocketMessage chatReply(Integer petId, String reply) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType("chat_reply");
        msg.setPetId(petId);
        msg.setReply(reply);
        msg.setTimestamp(System.currentTimeMillis());
        return msg;
    }

    /**
     * 创建正在输入消息
     */
    public static WebSocketMessage typing(Integer petId) {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType("typing");
        msg.setPetId(petId);
        msg.setTimestamp(System.currentTimeMillis());
        return msg;
    }
}
