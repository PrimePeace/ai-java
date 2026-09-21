package com.ai.aijava.agent.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 生成训练集请求
 */
@Data
public class GenerateDatasetRequest {

    @NotNull(message = "知识库 ID 不能为空")
    private Long kbId;

    @NotBlank(message = "数据集名称不能为空")
    @Size(max = 128, message = "数据集名称最长 128 字符")
    private String name;

    @Size(max = 256, message = "描述最长 256 字符")
    private String description;

    /** 每个 chunk 生成的 Q&A 对数量，默认 3 */
    @Min(value = 1, message = "每切片问答对数最小为 1")
    @Max(value = 10, message = "每切片问答对数最大为 10")
    private Integer qaPerChunk = 3;
}
