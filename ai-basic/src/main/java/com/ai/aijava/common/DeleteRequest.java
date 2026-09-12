package com.ai.aijava.common;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通用删除请求
 * 仅包含待删除记录 ID 的简单请求体，适用于软删除场景。
 */
@Data
public class DeleteRequest implements Serializable {

    /** 待删除记录的主键 ID */
    private Long id;

    @Serial
    private static final long serialVersionUID = 1L;
}
