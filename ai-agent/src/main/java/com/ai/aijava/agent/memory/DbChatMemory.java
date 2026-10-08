package com.ai.aijava.agent.memory;

import com.ai.aijava.agent.config.AgentProperties;
import com.ai.aijava.agent.entity.ChatMessage;
import com.ai.aijava.agent.mapper.ChatMessageMapper;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ChatMemory 实现：直读直写 chat_message 表（单一数据源，设计 2.1 方案 C）
 *
 * conversationId 全项目约定 = String.valueOf(sessionId)。
 * chat memory（模型上下文窗口，get 读窗口）与 chat history（前端展示，
 * ChatSessionService.listMessages 读全量）共用本表，互不干扰。
 *
 * 不使用官方 MessageWindowChatMemory：其 saveAll 为全量覆盖语义，
 * 会删除窗口外历史并丢失 citations，破坏 chat_message 的 source of truth 地位。
 *
 * @Component 注册后自动覆盖 Spring AI 自动配置的 ChatMemory
 * （ChatMemoryAutoConfiguration 带 @ConditionalOnMissingBean，已查证）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbChatMemory implements ChatMemory {

    /** assistant 消息 metadata 中携带 citations JSON 的 key */
    public static final String CITATIONS_KEY = "citations";

    /** assistant 消息 metadata 中携带 prompt tokens 的 key */
    public static final String PROMPT_TOKENS_KEY = "promptTokens";

    /** assistant 消息 metadata 中携带 completion tokens 的 key */
    public static final String COMPLETION_TOKENS_KEY = "completionTokens";

    private final ChatMessageMapper chatMessageMapper;
    private final AgentProperties agentProperties;

    /**
     * 追加消息（仅 User/Assistant；单条 default 方法自动转调本方法）
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        long sessionId = parseSessionId(conversationId);
        for (Message message : messages) {
            if (message instanceof UserMessage || message instanceof AssistantMessage) {
                chatMessageMapper.insert(toEntity(sessionId, message));
            } else {
                log.warn("DbChatMemory 忽略不支持的消息类型: {}", message.getMessageType());
            }
        }
    }

    /**
     * 读取记忆窗口：最近 historyRounds*2 条 → turn 边界对齐
     * 头部剥离连续 assistant（窗口截断导致的不完整轮次），
     * 尾部剥离连续 user（流式失败遗留的无应答提问）
     */
    @Override
    public List<Message> get(String conversationId) {
        long sessionId = parseSessionId(conversationId);
        int limit = agentProperties.getHistoryRounds() * 2;
        List<ChatMessage> msgs = new ArrayList<>(chatMessageMapper.selectListByQuery(
                QueryWrapper.create()
                        .select(ChatMessage::getRole, ChatMessage::getContent)
                        .where(ChatMessage::getSessionId).eq(sessionId)
                        .orderBy(ChatMessage::getId, false)
                        .limit(limit)));
        if (msgs.isEmpty()) {
            return List.of();
        }
        Collections.reverse(msgs);
        // 头部：丢弃开头连续 assistant（不完整 turn 的尾巴）
        while (!msgs.isEmpty() && ChatMessage.ROLE_ASSISTANT.equals(msgs.getFirst().getRole())) {
            msgs.removeFirst();
        }
        // 尾部：剥离末尾连续 user（防 LLM 看到连续 user 无 assistant）
        while (!msgs.isEmpty() && ChatMessage.ROLE_USER.equals(msgs.getLast().getRole())) {
            msgs.removeLast();
        }
        return msgs.stream().<Message>map(m -> ChatMessage.ROLE_USER.equals(m.getRole())
                ? new UserMessage(m.getContent())
                : new AssistantMessage(m.getContent())).toList();
    }

    /**
     * 清空会话全部消息（ChatSessionService.deleteSession 复用）
     */
    @Override
    public void clear(String conversationId) {
        long sessionId = parseSessionId(conversationId);
        chatMessageMapper.deleteByQuery(QueryWrapper.create()
                .where(ChatMessage::getSessionId).eq(sessionId));
    }

    /**
     * Message → ChatMessage 实体（citations 从 metadata 取出落列）
     */
    private ChatMessage toEntity(long sessionId, Message message) {
        String citations = null;
        Integer promptTokens = null;
        Integer completionTokens = null;
        if (message.getMetadata() != null) {
            Object cite = message.getMetadata().get(CITATIONS_KEY);
            if (cite instanceof String s && !s.isBlank()) {
                citations = s;
            }
            // token 用量从 metadata 取出（Number 统一转 Integer，兼容 Long/Integer）
            if (message.getMetadata().get(PROMPT_TOKENS_KEY) instanceof Number n) {
                promptTokens = n.intValue();
            }
            if (message.getMetadata().get(COMPLETION_TOKENS_KEY) instanceof Number n) {
                completionTokens = n.intValue();
            }
        }
        boolean isUser = message instanceof UserMessage;
        return ChatMessage.builder()
                .sessionId(sessionId)
                .role(isUser ? ChatMessage.ROLE_USER : ChatMessage.ROLE_ASSISTANT)
                .content(message.getText() == null ? "" : message.getText())
                .citations(citations)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .createTime(LocalDateTime.now())
                .build();
    }

    /**
     * conversationId → sessionId（全项目约定 String.valueOf(sessionId)）
     */
    private long parseSessionId(String conversationId) {
        try {
            return Long.parseLong(conversationId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的会话 ID");
        }
    }
}
