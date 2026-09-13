package com.ai.aijava.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建提示词模板请求
 */
@Data
public class PromptTemplateCreateRequest {

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 64, message = "模板名称最长 64 字符")
    private String name;

    @Size(max = 256, message = "描述最长 256 字符")
    private String description;

    @NotBlank(message = "系统提示词模板不能为空")
    @Size(max = 4000, message = "系统提示词模板最长 4000 字符")
    private String systemTemplate;

    @NotBlank(message = "用户消息模板不能为空")
    @Size(max = 2000, message = "用户消息模板最长 2000 字符")
    private String userTemplate;
}
