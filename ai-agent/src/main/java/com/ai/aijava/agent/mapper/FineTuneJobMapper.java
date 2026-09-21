package com.ai.aijava.agent.mapper;

import com.ai.aijava.agent.entity.FineTuneJob;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 微调任务 Mapper
 */
@Mapper
public interface FineTuneJobMapper extends BaseMapper<FineTuneJob> {
}
