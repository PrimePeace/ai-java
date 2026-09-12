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
 * 对话会话实体，对应 chat_session 表（一个会话绑定一个知识库）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("chat_session")
public class ChatSession {

    /** 默认会话标题，首条 user 消息后截取前 20 字更新 */
    public static final String DEFAULT_TITLE = "新会话";

    /** 会话 ID（主键，自增） */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 关联知识库 ID */
    private Long kbId;

    /** 会话标题 */
    private String title;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最后活跃时间（有新消息即更新） */
    private LocalDateTime updateTime;
}
