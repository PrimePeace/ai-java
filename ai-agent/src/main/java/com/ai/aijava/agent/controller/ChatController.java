package com.ai.aijava.agent.controller;

import com.ai.aijava.agent.dto.request.ChatSendRequest;
import com.ai.aijava.agent.dto.request.ChatSessionCreateRequest;
import com.ai.aijava.agent.dto.vo.ChatMessageVO;
import com.ai.aijava.agent.dto.vo.ChatSessionVO;
import com.ai.aijava.agent.dto.vo.TokenUsageVO;
import com.ai.aijava.agent.service.ChatSessionService;
import com.ai.aijava.agent.service.RagChatService;
import com.ai.aijava.annotation.RequireLogin;
import com.ai.aijava.annotation.RequirePermission;
import com.ai.aijava.auth.Permissions;
import com.ai.aijava.common.BaseResponse;
import com.ai.aijava.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 对话接口（含 SSE 流式问答）
 */
@Tag(name = "对话")
@RequirePermission(Permissions.CHAT_USE)
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatSessionService chatSessionService;
    private final RagChatService ragChatService;

    @Operation(summary = "创建会话")
    @RequireLogin
    @PostMapping("/session/create")
    public BaseResponse<ChatSessionVO> createSession(@RequestBody @Valid ChatSessionCreateRequest request) {
        return ResultUtils.success(chatSessionService.create(request));
    }

    @Operation(summary = "会话列表")
    @RequireLogin
    @GetMapping("/session/list")
    public BaseResponse<List<ChatSessionVO>> listSessions(
            @RequestParam(required = false) Long kbId) {
        return ResultUtils.success(chatSessionService.listSessions(kbId));
    }

    @Operation(summary = "历史消息")
    @RequireLogin
    @GetMapping("/session/{sessionId}/messages")
    public BaseResponse<List<ChatMessageVO>> listMessages(@PathVariable Long sessionId) {
        return ResultUtils.success(chatSessionService.listMessages(sessionId));
    }

    @Operation(summary = "删除会话")
    @RequireLogin
    @DeleteMapping("/session/{sessionId}")
    public BaseResponse<Void> deleteSession(@PathVariable Long sessionId) {
        chatSessionService.deleteSession(sessionId);
        return ResultUtils.success(null);
    }

    @Operation(summary = "Token 消耗汇总")
    @RequireLogin
    @GetMapping("/token-usage/summary")
    public BaseResponse<TokenUsageVO> tokenUsageSummary() {
        return ResultUtils.success(chatSessionService.summaryTokenUsage());
    }

    @Operation(summary = "提问（SSE 流式响应，4.2 事件协议）")
    @RequireLogin
    @PostMapping(value = "/session/{sessionId}/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> send(@PathVariable Long sessionId,
                                               @RequestBody @Valid ChatSendRequest request) {
        return ragChatService.chat(sessionId, request.getQuestion());
    }
}
