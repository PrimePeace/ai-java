package com.ai.aijava.agent.service;

import cn.hutool.json.JSONUtil;
import com.ai.aijava.agent.dto.request.ChatSessionCreateRequest;
import com.ai.aijava.agent.dto.vo.ChatMessageVO;
import com.ai.aijava.agent.dto.vo.ChatSessionVO;
import com.ai.aijava.agent.dto.vo.CitationVO;
import com.ai.aijava.agent.dto.vo.TokenUsageVO;
import com.ai.aijava.agent.entity.ChatMessage;
import com.ai.aijava.agent.entity.ChatSession;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.mapper.ChatMessageMapper;
import com.ai.aijava.agent.mapper.ChatSessionMapper;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.ai.aijava.exception.ThrowUtils;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 会话服务：会话 CRUD + 前端全量历史展示
 * 模型记忆窗口（chat memory）已收编至 DbChatMemory：
 * getHistory / saveUserMessage / saveAssistantMessage 已删除，
 * RagChatService 统一走 chatMemory.add/get。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ChatMemory chatMemory;

    /**
     * 创建会话（kbId 绑定，title 默认）
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionVO create(ChatSessionCreateRequest request) {
        // 防御：用户上下文缺失时快速失败，避免 DB 约束异常变成 500
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        // 归属校验
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(request.getKbId());
        if (kb == null || !kb.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .kbId(request.getKbId())
                .title(ChatSession.DEFAULT_TITLE)
                .createTime(now)
                .updateTime(now)
                .build();
        chatSessionMapper.insert(session);
        return ChatSessionVO.builder()
                .id(session.getId())
                .kbId(session.getKbId())
                .kbName(kb.getName())
                .title(session.getTitle())
                .createTime(session.getCreateTime())
                .updateTime(session.getUpdateTime())
                .build();
    }

    /**
     * 会话列表（按最后活跃倒序，可按 kbId 过滤）
     */
    public List<ChatSessionVO> listSessions(Long kbId) {
        QueryWrapper qw = QueryWrapper.create()
                .where(ChatSession::getUserId).eq(UserContext.getUserId());
        if (kbId != null) {
            qw.and(ChatSession::getKbId).eq(kbId);
        }
        qw.orderBy(ChatSession::getUpdateTime, false);
        List<ChatSession> sessions = chatSessionMapper.selectListByQuery(qw);
        if (sessions.isEmpty()) {
            return List.of();
        }
        // join 查 kbName
        List<Long> kbIds = sessions.stream().map(ChatSession::getKbId).distinct().toList();
        List<KnowledgeBase> kbs = knowledgeBaseMapper.selectListByQuery(
                QueryWrapper.create().select(KnowledgeBase::getId, KnowledgeBase::getName)
                        .where(KnowledgeBase::getId).in(kbIds));
        var nameMap = kbs.stream().collect(Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName));
        return sessions.stream().map(s -> ChatSessionVO.builder()
                .id(s.getId())
                .kbId(s.getKbId())
                .kbName(nameMap.get(s.getKbId()))
                .title(s.getTitle())
                .createTime(s.getCreateTime())
                .updateTime(s.getUpdateTime())
                .build()).toList();
    }

    /**
     * 历史消息（全量 chat history，前端展示用；按 id 升序即时间序；含 citations 反序列化）
     * 注意与 DbChatMemory.get（模型记忆窗口）区分：本方法读全量、不做窗口裁剪
     */
    public List<ChatMessageVO> listMessages(Long sessionId) {
        getOwnedSession(sessionId);
        return chatMessageMapper.selectListByQuery(
                        QueryWrapper.create()
                                .where(ChatMessage::getSessionId).eq(sessionId)
                                .orderBy(ChatMessage::getId, true))
                .stream().map(msg -> {
                    List<CitationVO> cites = null;
                    if (msg.getCitations() != null && !msg.getCitations().isBlank()) {
                        try {
                            cites = JSONUtil.toList(msg.getCitations(), CitationVO.class);
                        } catch (Exception e) {
                            log.warn("citations JSON 解析失败", e);
                        }
                    }
                    return ChatMessageVO.builder()
                            .id(msg.getId())
                            .role(msg.getRole())
                            .content(msg.getContent())
                            .citations(cites)
                            .createTime(msg.getCreateTime())
                            .build();
                }).toList();
    }

    /**
     * 删除会话（消息清理复用 chatMemory.clear）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long sessionId) {
        getOwnedSession(sessionId);
        chatMemory.clear(String.valueOf(sessionId));
        chatSessionMapper.deleteById(sessionId);
    }

    /**
     * 更新会话 title（首次提问后截取前 20 字）
     */
    public void updateTitleIfNeeded(Long sessionId, String question) {
        ChatSession session = chatSessionMapper.selectOneById(sessionId);
        if (session != null && ChatSession.DEFAULT_TITLE.equals(session.getTitle())) {
            ChatSession update = new ChatSession();
            update.setId(sessionId);
            String title = question.length() > 20 ? question.substring(0, 20) : question;
            update.setTitle(title);
            chatSessionMapper.update(update);
        }
    }

    /**
     * 刷新会话活跃时间（只更新 update_time，避免 partial entity 导致其他字段被置 NULL）
     */
    public void refreshSessionActiveTime(Long sessionId) {
        ChatSession update = new ChatSession();
        update.setId(sessionId);
        update.setUpdateTime(LocalDateTime.now());
        chatSessionMapper.update(update);
    }

    /**
     * 当前用户 Token 消耗汇总
     * 两步查询避免 join：先取当前用户全部 sessionId，再聚合 chat_message 的 token 列；
     * 聚合采用只 select 两列后 Java 内存求和（会话量级小，可接受，规避自定义聚合列的版本差异）
     */
    public TokenUsageVO summaryTokenUsage() {
        Long userId = UserContext.getUserId();
        // 第一步：当前用户全部会话 ID
        List<Long> sessionIds = chatSessionMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(ChatSession::getId)
                                .where(ChatSession::getUserId).eq(userId))
                .stream().map(ChatSession::getId).toList();
        // 无会话则直接返回全 0，避免空 IN 子句
        if (sessionIds.isEmpty()) {
            return TokenUsageVO.builder()
                    .promptTokens(0L)
                    .completionTokens(0L)
                    .totalTokens(0L)
                    .build();
        }
        // 必须带上主键：只选 token 列时，两列都为 NULL 的历史消息会被 MyBatis 映射成 null 元素
        List<ChatMessage> messages = chatMessageMapper.selectListByQuery(
                QueryWrapper.create()
                        .select(ChatMessage::getId, ChatMessage::getPromptTokens, ChatMessage::getCompletionTokens)
                        .where(ChatMessage::getSessionId).in(sessionIds)
                        .and(ChatMessage::getRole).eq(ChatMessage.ROLE_ASSISTANT));
        long promptTokens = messages.stream()
                .filter(Objects::nonNull)
                .mapToLong(m -> m.getPromptTokens() == null ? 0L : m.getPromptTokens())
                .sum();
        long completionTokens = messages.stream()
                .filter(Objects::nonNull)
                .mapToLong(m -> m.getCompletionTokens() == null ? 0L : m.getCompletionTokens())
                .sum();
        return TokenUsageVO.builder()
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens)
                .build();
    }

    /**
     * 校验会话归属当前用户
     */
    public ChatSession getOwnedSession(Long sessionId) {
        ChatSession session = chatSessionMapper.selectOneById(sessionId);
        if (session == null || !session.getUserId().equals(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }
        return session;
    }
}
