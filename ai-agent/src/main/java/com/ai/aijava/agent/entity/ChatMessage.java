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
 * 对话消息实体，对应 chat_message 表（追加型，无 update_time）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("chat_message")
public class ChatMessage {

    /** 角色：用户 */
    public static final String ROLE_USER = "user";

    /** 角色：助手 */
    public static final String ROLE_ASSISTANT = "assistant";

    /** 消息 ID（主键，自增，自增序即时间序） */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属会话 ID */
    private Long sessionId;

    /** 角色（user/assistant） */
    private String role;

    /** 消息内容 */
    private String content;

    /** 引用 JSON 数组（仅 assistant 消息，内容快照） */
    private String citations;

    /** 创建时间 */
    private LocalDateTime createTime;
}
