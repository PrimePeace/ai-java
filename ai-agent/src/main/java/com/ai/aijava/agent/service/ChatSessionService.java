package com.ai.aijava.agent.service;

import cn.hutool.json.JSONUtil;
import com.ai.aijava.agent.dto.request.ChatSessionCreateRequest;
import com.ai.aijava.agent.dto.vo.ChatMessageVO;
import com.ai.aijava.agent.dto.vo.ChatSessionVO;
import com.ai.aijava.agent.dto.vo.CitationVO;
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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话与消息服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

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
        var nameMap = kbs.stream().collect(java.util.stream.Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName));
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
     * 历史消息（全量，按 id 升序即时间序；含 citations 反序列化）
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
     * 删除会话（级联删消息）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long sessionId) {
        getOwnedSession(sessionId);
        chatMessageMapper.deleteByQuery(QueryWrapper.create()
                .where(ChatMessage::getSessionId).eq(sessionId));
        chatSessionMapper.deleteById(sessionId);
    }

    /**
     * 保存用户提问
     */
    public Long saveUserMessage(Long sessionId, String question) {
        ChatMessage msg = ChatMessage.builder()
                .sessionId(sessionId)
                .role(ChatMessage.ROLE_USER)
                .content(question)
                .createTime(LocalDateTime.now())
                .build();
        chatMessageMapper.insert(msg);
        return msg.getId();
    }

    /**
     * 保存助手回答（含 citations JSON 序列化）
     */
    public void saveAssistantMessage(Long sessionId, String answer, String citationsJson) {
        ChatMessage msg = ChatMessage.builder()
                .sessionId(sessionId)
                .role(ChatMessage.ROLE_ASSISTANT)
                .content(answer)
                .citations(citationsJson)
                .createTime(LocalDateTime.now())
                .build();
        chatMessageMapper.insert(msg);
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
     * 获取历史上下文（最近 N 轮对话），丢弃末尾连续的孤立 user 消息
     *
     * 设计 4.2 决策 #6：流失败遗留的无应答 user 不进 history，防止模型看到连续 user 无 assistant
     */
    public List<Message> getHistory(Long sessionId, int historyRounds) {
        int limit = historyRounds * 2;
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
        while (!msgs.isEmpty() && ChatMessage.ROLE_USER.equals(msgs.getLast().getRole())) {
            msgs.removeLast();
        }
        return msgs.stream().<Message>map(m -> {
            if (ChatMessage.ROLE_USER.equals(m.getRole())) {
                return new UserMessage(m.getContent());
            }
            return new AssistantMessage(m.getContent());
        }).toList();
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
