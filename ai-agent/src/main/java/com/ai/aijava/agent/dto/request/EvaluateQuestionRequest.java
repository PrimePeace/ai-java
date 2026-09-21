package com.ai.aijava.agent.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发起评测请求（kbId 由路径变量注入）
 */
@Data
public class EvaluateQuestionRequest {

    /** 知识库 ID（Controller 从路径变量设置） */
    private Long kbId;

    /** 测试问题列表（为空时从历史对话自动抽取） */
    @Size(max = 50, message = "单次评测问题数最多 50 个")
    private List<String> questions;

    /** 自动抽取的最大问题数，默认 10 */
    @Min(value = 1, message = "抽取数量最小为 1")
    @Max(value = 50, message = "抽取数量最大为 50")
    private Integer maxExtractCount = 10;
}
