package com.ai.aijava.agent.service;

import cn.hutool.json.JSONUtil;
import com.ai.aijava.agent.dto.request.EvaluateQuestionRequest;
import com.ai.aijava.agent.dto.vo.EvaluationRecordVO;
import com.ai.aijava.agent.entity.ChatMessage;
import com.ai.aijava.agent.entity.ChatSession;
import com.ai.aijava.agent.entity.EvaluationRecord;
import com.ai.aijava.agent.entity.FineTuneDataset;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.enums.DatasetStatus;
import com.ai.aijava.agent.mapper.ChatMessageMapper;
import com.ai.aijava.agent.mapper.ChatSessionMapper;
import com.ai.aijava.agent.mapper.EvaluationRecordMapper;
import com.ai.aijava.agent.mapper.FineTuneDatasetMapper;
import com.ai.aijava.agent.util.LlmJsonExtractor;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.ErrorCode;
import com.ai.aijava.exception.ThrowUtils;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测服务：同一批问题并行走「纯 RAG」与「RAG + 风格」两路链路，人工评分为主 + LLM 裁判自动评分辅助
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final EvaluationRecordMapper evaluationRecordMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final FineTuneDatasetMapper fineTuneDatasetMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KbRetriever kbRetriever;
    private final PromptTemplateService promptTemplateService;
    private final ChatModel chatModel;

    /** 自注入代理：@Async 自调用失效，必须经代理 */
    @Resource
    @Lazy
    private EvaluationService self;

    /**
     * 发起评测：同步落记录（立即返回），异步执行双链路回答 + 自动评分
     */
    public List<EvaluationRecordVO> run(EvaluateQuestionRequest request) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        KnowledgeBase kb = knowledgeBaseService.getOwnedKb(request.getKbId());

        List<String> questions = request.getQuestions() == null ? List.of() : request.getQuestions().stream()
                .filter(q -> q != null && !q.isBlank()).map(String::trim).distinct().toList();
        if (questions.isEmpty()) {
            int maxCount = request.getMaxExtractCount() != null ? request.getMaxExtractCount() : 10;
            // 优先从最新 READY 数据集的 JSONL 抽取 user 问题（最贴合 KB 领域），无数据集再回退历史对话
            questions = extractQuestionsFromDataset(kb.getId(), maxCount);
            if (questions.isEmpty()) {
                questions = extractQuestionsFromHistory(kb.getId(), maxCount);
            }
        }
        ThrowUtils.throwIf(questions.isEmpty(), ErrorCode.PARAMS_ERROR,
                "没有可评测的问题（请手动输入，或先生成数据集 / 产生对话）");

        LocalDateTime now = LocalDateTime.now();
        List<EvaluationRecord> records = new ArrayList<>(questions.size());
        for (String question : questions) {
            EvaluationRecord record = EvaluationRecord.builder()
                    .kbId(kb.getId())
                    .question(question)
                    .createTime(now)
                    .updateTime(now)
                    .build();
            evaluationRecordMapper.insert(record);
            records.add(record);
        }
        self.runEvaluationAsync(records, kb);
        return records.stream().map(this::toVO).toList();
    }

    /**
     * 手动评分（人工评分为主，可只评其中一路）
     */
    public void score(Long recordId, Integer ragScore, Integer styleScore) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        validateScore(ragScore);
        validateScore(styleScore);
        EvaluationRecord record = evaluationRecordMapper.selectOneById(recordId);
        ThrowUtils.throwIf(record == null, ErrorCode.NOT_FOUND_ERROR, "评测记录不存在");
        knowledgeBaseService.getOwnedKb(record.getKbId());
        EvaluationRecord update = new EvaluationRecord();
        update.setId(recordId);
        update.setRagScore(ragScore);
        update.setStyleScore(styleScore);
        evaluationRecordMapper.update(update);
    }

    /**
     * KB 下评测记录列表（归属校验）
     */
    public List<EvaluationRecordVO> list(Long kbId) {
        knowledgeBaseService.getOwnedKb(kbId);
        return evaluationRecordMapper.selectListByQuery(
                        QueryWrapper.create()
                                .where(EvaluationRecord::getKbId).eq(kbId)
                                .orderBy(EvaluationRecord::getCreateTime, false))
                .stream().map(this::toVO).toList();
    }

    /**
     * 评测汇总：平均分 / 胜率（仅统计人工双评分已完成的记录）
     */
    public Map<String, Object> summary(Long kbId) {
        knowledgeBaseService.getOwnedKb(kbId);
        List<EvaluationRecord> records = evaluationRecordMapper.selectListByQuery(
                QueryWrapper.create().where(EvaluationRecord::getKbId).eq(kbId));
        if (records.isEmpty()) {
            return Map.of("total", 0, "scoredCount", 0, "avgRagScore", 0, "avgStyleScore", 0,
                    "styleWins", 0, "ragWins", 0, "ties", 0);
        }
        List<EvaluationRecord> scored = records.stream()
                .filter(r -> r.getRagScore() != null && r.getStyleScore() != null).toList();
        double avgRag = scored.stream().mapToInt(EvaluationRecord::getRagScore).average().orElse(0);
        double avgStyle = scored.stream().mapToInt(EvaluationRecord::getStyleScore).average().orElse(0);
        long styleWins = scored.stream().filter(r -> r.getStyleScore() > r.getRagScore()).count();
        long ragWins = scored.stream().filter(r -> r.getRagScore() > r.getStyleScore()).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", records.size());
        result.put("scoredCount", scored.size());
        result.put("avgRagScore", Math.round(avgRag * 100.0) / 100.0);
        result.put("avgStyleScore", Math.round(avgStyle * 100.0) / 100.0);
        result.put("styleWins", styleWins);
        result.put("ragWins", ragWins);
        result.put("ties", scored.size() - styleWins - ragWins);
        return result;
    }

    /**
     * 异步执行评测：每条记录分别跑「纯 RAG」与「RAG + 风格」两路链路，再 LLM 裁判自动评分
     */
    @Async("fineTuneTaskExecutor")
    public void runEvaluationAsync(List<EvaluationRecord> records, KnowledgeBase kb) {
        for (EvaluationRecord record : records) {
            try {
                String ragAnswer = runRagChain(kb, record.getQuestion(), false);
                String styleAnswer = runRagChain(kb, record.getQuestion(), true);
                EvaluationRecord update = new EvaluationRecord();
                update.setId(record.getId());
                update.setRagAnswer(truncate(ragAnswer, 6000));
                update.setStyleAnswer(truncate(styleAnswer, 6000));
                evaluationRecordMapper.update(update);
                autoEvaluate(record.getId(), record.getQuestion(), ragAnswer, styleAnswer);
            } catch (Exception e) {
                log.error("评测执行失败 recordId={}", record.getId(), e);
            }
        }
    }

    /**
     * RAG 链路评测：检索 → 模板渲染 → 同步调用（复用 KbRetriever / PromptTemplateService，不走 SSE）
     *
     * @param withStyle true 时在 system 提示词后叠加 KB 的 style_prompt（RAG + 风格链路）
     */
    private String runRagChain(KnowledgeBase kb, String question, boolean withStyle) {
        List<Document> hits = kbRetriever.retrieve(kb.getId(), question);
        String refText = kbRetriever.buildReferences(hits);
        String systemText = promptTemplateService.renderSystem(kb);
        if (withStyle && kb.getStylePrompt() != null && !kb.getStylePrompt().isBlank()) {
            systemText += "\n\n回答风格要求：\n" + kb.getStylePrompt().trim();
        }
        List<Message> messages = List.of(
                new SystemMessage(systemText),
                new UserMessage(promptTemplateService.renderUser(kb, refText, question)));
        ChatResponse response = chatModel.call(new Prompt(messages));
        return response.getResult() != null && response.getResult().getOutput() != null
                ? response.getResult().getOutput().getText() : "";
    }

    /**
     * 从最新 READY 数据集的 ChatML JSONL 中抽取 user 问题（每行 messages 中 role=user 的 content）
     */
    private List<String> extractQuestionsFromDataset(Long kbId, int maxCount) {
        List<FineTuneDataset> datasets = fineTuneDatasetMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(FineTuneDataset::getKbId).eq(kbId)
                        .and(FineTuneDataset::getStatus).eq(DatasetStatus.READY.name())
                        .orderBy(FineTuneDataset::getCreateTime, false)
                        .limit(1));
        if (datasets.isEmpty()) {
            return List.of();
        }
        String filePath = datasets.get(0).getFilePath();
        if (filePath == null || filePath.isBlank() || !Files.exists(Path.of(filePath))) {
            return List.of();
        }
        try {
            List<String> questions = new ArrayList<>();
            for (String line : Files.readAllLines(Path.of(filePath))) {
                if (line.isBlank()) {
                    continue;
                }
                var messages = JSONUtil.parseObj(line).getJSONArray("messages");
                for (Object msg : messages) {
                    var msgObj = JSONUtil.parseObj(msg);
                    if ("user".equals(msgObj.getStr("role"))) {
                        String content = msgObj.getStr("content");
                        if (content != null && !content.isBlank()) {
                            questions.add(content.trim());
                        }
                        break;
                    }
                }
                if (questions.size() >= maxCount) {
                    break;
                }
            }
            return questions;
        } catch (Exception e) {
            log.warn("从数据集抽取评测问题失败 kbId={}", kbId, e);
            return List.of();
        }
    }

    /**
     * 从历史对话抽取测试问题：取 KB 每个会话的首条 user 消息（按会话去重）
     */
    private List<String> extractQuestionsFromHistory(Long kbId, int maxCount) {
        List<Long> sessionIds = chatSessionMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(ChatSession::getId)
                                .where(ChatSession::getKbId).eq(kbId))
                .stream().map(ChatSession::getId).toList();
        if (sessionIds.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> userMessages = chatMessageMapper.selectListByQuery(
                QueryWrapper.create()
                        .select(ChatMessage::getSessionId, ChatMessage::getContent)
                        .where(ChatMessage::getSessionId).in(sessionIds)
                        .and(ChatMessage::getRole).eq(ChatMessage.ROLE_USER)
                        .orderBy(ChatMessage::getId, true));
        // 每个会话只取首条提问（LinkedHashMap 保留消息顺序）
        Map<Long, String> firstBySession = new LinkedHashMap<>();
        for (ChatMessage msg : userMessages) {
            firstBySession.putIfAbsent(msg.getSessionId(), msg.getContent());
        }
        return firstBySession.values().stream()
                .filter(c -> c != null && !c.isBlank())
                .limit(maxCount)
                .toList();
    }

    /**
     * 自动评测：LLM 裁判对比两个回答，输出 0.00-1.00 分数与评语
     */
    private void autoEvaluate(Long recordId, String question, String ragAnswer, String styleAnswer) {
        String systemText = "你是一个公正的评测裁判。请基于问题，对比两个回答的准确性、完整性和专业性，"
                + "分别给出 0.00-1.00 的分数，并给出简短评语。仅以 JSON 格式输出：\n"
                + "{\"ragScore\": 0.85, \"styleScore\": 0.90, \"comment\": \"...\"}";
        String userText = String.format("问题：%s\n\n回答A（纯RAG）：%s\n\n回答B（RAG+风格）：%s",
                question, truncate(ragAnswer, 1500), truncate(styleAnswer, 1500));
        try {
            ChatResponse response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(systemText), new UserMessage(userText))));
            String output = response.getResult() != null && response.getResult().getOutput() != null
                    ? response.getResult().getOutput().getText() : "{}";
            Map<String, Object> scores = JSONUtil.toBean(LlmJsonExtractor.extractObject(output), Map.class);
            EvaluationRecord update = new EvaluationRecord();
            update.setId(recordId);
            update.setAutoScoreRag(parseScore(scores.get("ragScore")));
            update.setAutoScoreStyle(parseScore(scores.get("styleScore")));
            update.setEvaluatorComment(truncate((String) scores.get("comment"), 500));
            evaluationRecordMapper.update(update);
        } catch (Exception e) {
            log.warn("自动评测失败 recordId={}", recordId, e);
        }
    }

    private static BigDecimal parseScore(Object value) {
        try {
            BigDecimal score = new BigDecimal(String.valueOf(value));
            return score.max(BigDecimal.ZERO).min(BigDecimal.ONE);
        } catch (Exception e) {
            return new BigDecimal("0.50");
        }
    }

    private static void validateScore(Integer score) {
        ThrowUtils.throwIf(score != null && (score < 1 || score > 5),
                ErrorCode.PARAMS_ERROR, "评分范围为 1-5");
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private EvaluationRecordVO toVO(EvaluationRecord record) {
        return EvaluationRecordVO.builder()
                .id(record.getId())
                .kbId(record.getKbId())
                .question(record.getQuestion())
                .ragAnswer(record.getRagAnswer())
                .styleAnswer(record.getStyleAnswer())
                .ragScore(record.getRagScore())
                .styleScore(record.getStyleScore())
                .autoScoreRag(record.getAutoScoreRag())
                .autoScoreStyle(record.getAutoScoreStyle())
                .evaluatorComment(record.getEvaluatorComment())
                .createTime(record.getCreateTime())
                .updateTime(record.getUpdateTime())
                .build();
    }
}
