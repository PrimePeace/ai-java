package com.ai.aijava.agent.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建会话请求
 */
@Data
public class ChatSessionCreateRequest {

    @NotNull(message = "知识库 ID 不能为空")
    private Long kbId;
}
