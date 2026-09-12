package com.ai.aijava.agent.mapper;

import com.ai.aijava.agent.entity.KnowledgeDocument;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档 Mapper
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}
