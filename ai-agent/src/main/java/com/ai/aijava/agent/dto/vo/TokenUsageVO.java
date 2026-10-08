package com.ai.aijava.agent.dto.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 消耗汇总视图对象（当前登录用户全量会话累计）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsageVO {

    /** 累计输入 token 数 */
    private Long promptTokens;

    /** 累计输出 token 数 */
    private Long completionTokens;

    /** 累计总 token 数（promptTokens + completionTokens） */
    private Long totalTokens;
}
