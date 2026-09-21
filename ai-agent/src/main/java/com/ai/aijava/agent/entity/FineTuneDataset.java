package com.ai.aijava.agent.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 微调数据集实体，对应 fine_tune_dataset 表
 * JSONL 文件存磁盘（uploads/fine_tune/{datasetId}.jsonl），本表只存路径与统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("fine_tune_dataset")
public class FineTuneDataset {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属知识库 ID */
    private Long kbId;

    private String name;

    private String description;

    /** 数据格式：chatml */
    private String format;

    /** JSONL 文件存储路径 */
    private String filePath;

    /** 样本数（Q&A 对数量） */
    private Integer sampleCount;

    /** GENERATING / READY / FAILED */
    private String status;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
