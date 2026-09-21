package com.ai.aijava.agent.service;

import com.ai.aijava.agent.entity.KnowledgeBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 风格蒸馏辅助：style_prompt 组装 + 风格增强同步问答（评测链路复用）
 *
 * <p>已废弃官方微调模型直调（OpenAiChatOptions 覆盖 model=ftModelId）；
 * 统一使用全局默认 ChatModel（glm-5.3-flash），风格能力通过 system 文案叠加 style_prompt 实现。
 * 类名保留仅为兼容 EvaluationService 现有调用，后续评测链路改造时可一并更名。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FineTuneChatService {

    private final ChatModel chatModel;
    private final PromptTemplateService promptTemplateService;

    /**
     * 是否叠加 style_prompt：style 引擎强制叠加；auto 引擎有 stylePrompt 时叠加
     */
    public static boolean shouldApplyStyle(KnowledgeBase kb) {
        String engine = kb.getChatEngine();
        boolean engineHit = "style".equals(engine) || "auto".equals(engine);
        return engineHit && kb.getStylePrompt() != null && !kb.getStylePrompt().isBlank();
    }

    /**
     * 组装 system 文案：基础模板渲染结果 + 按需追加 style_prompt
     */
    public String buildSystemText(KnowledgeBase kb) {
        String systemText = promptTemplateService.renderSystem(kb);
        if (shouldApplyStyle(kb)) {
            systemText = systemText + "\n\n" + kb.getStylePrompt().trim();
        }
        return systemText;
    }

    /**
     * 风格增强同步问答（评测用，非流式）：默认模型 + 风格 system 文案，不再按 ftModelId 覆盖模型
     */
    public String callFt(KnowledgeBase kb, String question) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(buildSystemText(kb)),
                new UserMessage(question)));
        ChatResponse response = chatModel.call(prompt);
        return response.getResult() != null && response.getResult().getOutput() != null
                ? response.getResult().getOutput().getText() : "";
    }
}
