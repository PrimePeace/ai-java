package com.ai.aijava.agent.mapper;

import com.ai.aijava.agent.entity.ChatSession;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话会话 Mapper
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
