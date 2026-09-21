package com.ai.aijava.agent.service;

import com.ai.aijava.agent.entity.KnowledgeBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 微调模型对话服务：直调智谱聊天 API（model=ftModelId），不走向量检索
 * 复用同一 OpenAI 兼容 ChatModel，仅通过 OpenAiChatOptions 覆盖 model
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FineTuneChatService {

    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private final ChatSessionService chatSessionService;
    private final PromptTemplateService promptTemplateService;
    private final SseChatAssembler sseChatAssembler;

    /**
     * 是否应走微调模型链路：ft 强制；auto 有绑定模型时走微调
     */
    public static boolean shouldUseFineTune(KnowledgeBase kb) {
        String engine = kb.getChatEngine();
        boolean engineHit = "ft".equals(engine) || "auto".equals(engine);
        return engineHit && kb.getFtModelId() != null && !kb.getFtModelId().isBlank();
    }

    /**
     * 微调模型流式问答（SSE）：无检索、无引用，历史窗口与 RAG 链路一致
     */
    public Flux<ServerSentEvent<String>> streamChat(Long sessionId, KnowledgeBase kb, String question) {
        List<Message> history = chatMemory.get(String.valueOf(sessionId));
        List<Message> messages = new ArrayList<>(history.size() + 2);
        messages.add(new SystemMessage(promptTemplateService.renderSystem(kb)));
        messages.addAll(history);
        messages.add(new UserMessage(question));
        Prompt prompt = new Prompt(messages, ftOptions(kb.getFtModelId()));

        chatMemory.add(String.valueOf(sessionId), new UserMessage(question));
        chatSessionService.updateTitleIfNeeded(sessionId, question);

        return sseChatAssembler.assemble(chatModel.stream(prompt), sessionId, List.of());
    }

    /**
     * 微调模型同步问答（评测用，非流式）
     */
    public String callFt(KnowledgeBase kb, String question) {
        Prompt prompt = new Prompt(
                List.of(new UserMessage(question)), ftOptions(kb.getFtModelId()));
        ChatResponse response = chatModel.call(prompt);
        return response.getResult() != null && response.getResult().getOutput() != null
                ? response.getResult().getOutput().getText() : "";
    }

    private static OpenAiChatOptions ftOptions(String ftModelId) {
        return OpenAiChatOptions.builder().model(ftModelId).build();
    }
}
