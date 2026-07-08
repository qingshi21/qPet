package com.lumenami.backend.mapper;

import com.lumenami.backend.model.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    void insert(ChatMessage message);

    List<ChatMessage> findByPetId(@Param("petId") Integer petId);

    void deleteByPetId(@Param("petId") Integer petId);
}
