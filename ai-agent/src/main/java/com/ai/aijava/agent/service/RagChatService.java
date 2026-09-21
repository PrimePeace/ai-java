package com.ai.aijava.agent.service;

import com.ai.aijava.agent.dto.vo.CitationVO;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.exception.BusinessException;
import com.ai.aijava.exception.ErrorCode;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 流式问答（核心）
 * 所有 KB 始终走 RAG 检索链路；style/auto 引擎且有 stylePrompt 时，在 system 文案末尾叠加风格提示词
 * RAG 链路：检索（KbRetriever）→ Prompt 模板渲染（+style_prompt 叠加）→ ChatMemory 历史窗口 → ChatModel 流式 → SSE 推送
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    private final ChatModel chatModel;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ChatSessionService chatSessionService;
    private final PromptTemplateService promptTemplateService;
    private final ChatMemory chatMemory;
    private final KbRetriever kbRetriever;
    private final SseChatAssembler sseChatAssembler;
    private final FineTuneChatService fineTuneChatService;

    /**
     * 会话提问 → SSE Flux（4.2 事件协议）
     */
    public Flux<ServerSentEvent<String>> chat(Long sessionId, String question) {
        try {
            return doChat(sessionId, question);
        } catch (BusinessException e) {
            return sseChatAssembler.errorFlux(e.getMessage());
        } catch (Exception e) {
            log.error("RAG 问答准备阶段异常", e);
            return sseChatAssembler.errorFlux("问答失败，请稍后重试");
        }
    }

    private Flux<ServerSentEvent<String>> doChat(Long sessionId, String question) {
        // 归属校验（取会话 → 取 kbId → 查 KB 用于模板渲染与 style_prompt 叠加判断）
        var session = chatSessionService.getOwnedSession(sessionId);
        Long kbId = session.getKbId();
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(kbId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }

        // 所有 KB 始终走 RAG 链路：检索 → 过滤 → 截断 topK
        List<Document> hits = kbRetriever.retrieve(kbId, question);
        List<CitationVO> citations = kbRetriever.buildCitations(hits);

        // 历史记忆窗口（turn 对齐；此时本轮 user 尚未落库）
        List<Message> history = chatMemory.get(String.valueOf(sessionId));

        // Prompt 模板渲染（未绑定 KB 用默认模板，行为与旧硬编码等价）
        // system 文案 = 模板渲染结果；style/auto 且有 stylePrompt 时追加 style_prompt
        String refText = kbRetriever.buildReferences(hits);
        String systemText = fineTuneChatService.buildSystemText(kb);
        String userText = promptTemplateService.renderUser(kb, refText, question);
        List<Message> messages = new ArrayList<>(history.size() + 2);
        messages.add(new SystemMessage(systemText));
        messages.addAll(history);
        messages.add(new UserMessage(userText));
        Prompt prompt = new Prompt(messages);

        // user 消息落库（chatMemory.add）
        chatMemory.add(String.valueOf(sessionId), new UserMessage(question));
        chatSessionService.updateTitleIfNeeded(sessionId, question);

        // 流式生成 + 装配 SSE
        return sseChatAssembler.assemble(chatModel.stream(prompt), sessionId, citations);
    }
}
