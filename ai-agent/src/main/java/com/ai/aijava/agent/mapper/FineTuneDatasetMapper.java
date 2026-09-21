package com.ai.aijava.agent.mapper;

import com.ai.aijava.agent.entity.FineTuneDataset;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 微调数据集 Mapper
 */
@Mapper
public interface FineTuneDatasetMapper extends BaseMapper<FineTuneDataset> {
}
