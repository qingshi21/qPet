package com.lumenami.backend.mapper;

import com.lumenami.backend.model.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    void insert(ChatMessage message);

    List<ChatMessage> findByPetId(@Param("petId") Integer petId);

    /**
     * 查找最近N条消息（按时间倒序）
     */
    List<ChatMessage> findRecentByPetId(@Param("petId") Integer petId, @Param("limit") Integer limit);

    /**
     * 更新消息的embedding
     */
    void updateEmbedding(@Param("id") Integer id, @Param("embedding") String embedding);

    void deleteByPetId(@Param("petId") Integer petId);
}
