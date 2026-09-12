package com.ai.aijava.agent.mapper;

import com.ai.aijava.agent.entity.DocumentChunk;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档切片 Mapper
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {
}
