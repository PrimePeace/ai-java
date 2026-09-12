package com.ai.aijava.agent.mapper;

import com.ai.aijava.agent.entity.ChatMessage;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话消息 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
