package com.ai.aijava.agent.service;

import cn.hutool.json.JSONUtil;
import com.ai.aijava.agent.dto.vo.CitationVO;
import com.ai.aijava.agent.memory.DbChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SSE 事件流装配器：ChatModel Flux → message / citations / end / error 事件
 * 从 RagChatService 抽取，供 RAG 链路与微调模型链路共用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseChatAssembler {

    private final ChatMemory chatMemory;
    private final ChatSessionService chatSessionService;

    /**
     * 装配 SSE 事件流：聚合全文落库（含 citations）→ citations 事件 → end 事件；异常转 error 事件
     */
    public Flux<ServerSentEvent<String>> assemble(Flux<ChatResponse> responseFlux, Long sessionId,
                                                  List<CitationVO> citations) {
        StringBuilder aggregated = new StringBuilder();
        // 记录流式过程中最后一个非空 usage（GLM 流式在末尾 chunk 返回 token 用量）
        AtomicReference<Usage> lastUsage = new AtomicReference<>();
        return responseFlux
                .doOnNext(chunk -> {
                    String content = chunk.getResult() != null && chunk.getResult().getOutput() != null
                            ? chunk.getResult().getOutput().getText() : "";
                    if (content != null && !content.isBlank()) {
                        aggregated.append(content);
                    }
                    // 采集真实 token 用量：保留最后一个非空 usage
                    if (chunk.getMetadata() != null && chunk.getMetadata().getUsage() != null) {
                        lastUsage.set(chunk.getMetadata().getUsage());
                    }
                })
                .mapNotNull(chunk -> {
                    String content = chunk.getResult() != null && chunk.getResult().getOutput() != null
                            ? chunk.getResult().getOutput().getText() : "";
                    if (content == null || content.isBlank()) {
                        return null;
                    }
                    return ServerSentEvent.<String>builder()
                            .event("message")
                            .data(content)
                            .build();
                })
                .doOnComplete(() -> {
                    try {
                        String citationsJson = JSONUtil.toJsonStr(citations);
                        // assistant 落库：citations、token 用量通过 metadata 传递（DbChatMemory 取出落列）
                        Map<String, Object> properties = new HashMap<>();
                        properties.put(DbChatMemory.CITATIONS_KEY, citationsJson);
                        // 仅在有 usage 时写入 token 用量（null 不写入、不报错）
                        Usage usage = lastUsage.get();
                        if (usage != null) {
                            if (usage.getPromptTokens() != null) {
                                properties.put(DbChatMemory.PROMPT_TOKENS_KEY, usage.getPromptTokens());
                            }
                            if (usage.getCompletionTokens() != null) {
                                properties.put(DbChatMemory.COMPLETION_TOKENS_KEY, usage.getCompletionTokens());
                            }
                        }
                        chatMemory.add(String.valueOf(sessionId), AssistantMessage.builder()
                                .content(aggregated.toString())
                                .properties(properties)
                                .build());
                        chatSessionService.refreshSessionActiveTime(sessionId);
                    } catch (Exception e) {
                        log.error("保存助手消息失败", e);
                    }
                })
                .concatWith(
                        Mono.just(ServerSentEvent.<String>builder()
                                .event("citations")
                                .data(JSONUtil.toJsonStr(citations))
                                .build()))
                .concatWith(Mono.just(ServerSentEvent.<String>builder().event("end").build()))
                .onErrorResume(e -> {
                    log.error("问答流式异常", e);
                    return Mono.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("回答失败：" + (e.getMessage() != null ? e.getMessage().replaceAll("[\\r\\n]", "") : "未知错误"))
                            .build());
                });
    }

    /**
     * 构造单 error 事件流（准备阶段异常兜底）
     */
    public Flux<ServerSentEvent<String>> errorFlux(String msg) {
        String safe = msg == null ? "未知错误" : msg.replaceAll("[\\r\\n]", "");
        return Flux.just(ServerSentEvent.<String>builder()
                .event("error")
                .data(safe)
                .build());
    }
}
