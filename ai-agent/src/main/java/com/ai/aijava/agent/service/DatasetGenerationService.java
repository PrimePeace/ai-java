package com.ai.aijava.agent.service;

import cn.hutool.json.JSONUtil;
import com.ai.aijava.agent.config.AgentProperties;
import com.ai.aijava.agent.dto.request.GenerateDatasetRequest;
import com.ai.aijava.agent.dto.vo.FineTuneDatasetVO;
import com.ai.aijava.agent.entity.DocumentChunk;
import com.ai.aijava.agent.entity.FineTuneDataset;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.entity.KnowledgeDocument;
import com.ai.aijava.agent.enums.DatasetStatus;
import com.ai.aijava.agent.enums.DocStatus;
import com.ai.aijava.agent.mapper.DocumentChunkMapper;
import com.ai.aijava.agent.mapper.FineTuneDatasetMapper;
import com.ai.aijava.agent.mapper.KnowledgeDocumentMapper;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据集生成服务：KB chunk（COMPLETED 文档）→ LLM 生成 Q&A → 导出 ChatML JSONL
 * 每个 Q&A 对一行：{"messages":[system,user,assistant]}（智谱 SFT 原生格式）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetGenerationService {

    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final FineTuneDatasetMapper fineTuneDatasetMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final PromptTemplateService promptTemplateService;
    private final ChatModel chatModel;
    private final AgentProperties agentProperties;

    /** 自注入代理：@Async 自调用失效，必须经代理（同 KnowledgeBaseService 模式） */
    @Resource
    @Lazy
    private DatasetGenerationService self;

    /**
     * 创建数据集生成任务（同步建记录 + 归属校验，立即返回，异步生成）
     */
    public FineTuneDatasetVO generate(GenerateDatasetRequest request) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        KnowledgeBase kb = knowledgeBaseService.getOwnedKb(request.getKbId());

        // 同步阶段渲染 system 文本（@Async 线程无 UserContext，不能延迟到异步阶段）
        String systemText = promptTemplateService.renderSystem(kb);
        int qaPerChunk = request.getQaPerChunk() != null
                ? request.getQaPerChunk() : agentProperties.getFineTune().getQaPerChunk();

        LocalDateTime now = LocalDateTime.now();
        FineTuneDataset dataset = FineTuneDataset.builder()
                .kbId(kb.getId())
                .name(request.getName())
                .description(request.getDescription() == null ? "" : request.getDescription())
                .format("chatml")
                .filePath("")
                .sampleCount(0)
                .status(DatasetStatus.GENERATING.name())
                .createTime(now)
                .updateTime(now)
                .build();
        fineTuneDatasetMapper.insert(dataset);

        self.doGenerateAsync(dataset.getId(), kb.getId(), qaPerChunk, systemText);
        return toVO(dataset);
    }

    /**
     * KB 下数据集列表（归属校验）
     */
    public List<FineTuneDatasetVO> listMine(Long kbId) {
        knowledgeBaseService.getOwnedKb(kbId);
        return fineTuneDatasetMapper.selectListByQuery(
                        QueryWrapper.create()
                                .where(FineTuneDataset::getKbId).eq(kbId)
                                .orderBy(FineTuneDataset::getCreateTime, false))
                .stream().map(this::toVO).toList();
    }

    /**
     * 删除数据集（含磁盘 JSONL 文件，文件删除失败仅记日志）
     */
    public void delete(Long datasetId) {
        FineTuneDataset dataset = fineTuneDatasetMapper.selectOneById(datasetId);
        ThrowUtils.throwIf(dataset == null, ErrorCode.NOT_FOUND_ERROR, "数据集不存在");
        knowledgeBaseService.getOwnedKb(dataset.getKbId());
        if (dataset.getFilePath() != null && !dataset.getFilePath().isBlank()) {
            try {
                Files.deleteIfExists(Paths.get(dataset.getFilePath()));
            } catch (Exception e) {
                log.warn("数据集文件删除失败: {}", dataset.getFilePath(), e);
            }
        }
        fineTuneDatasetMapper.deleteById(datasetId);
    }

    /**
     * 异步生成：逐 chunk 调 LLM 生成 Q&A（单 chunk 失败跳过不影响整体），写 JSONL
     */
    @Async("fineTuneTaskExecutor")
    public void doGenerateAsync(Long datasetId, Long kbId, int qaPerChunk, String systemText) {
        try {
            List<DocumentChunk> chunks = listCompletedChunks(kbId);
            if (chunks.isEmpty()) {
                markFailed(datasetId, "知识库中没有已处理完成的文档切片");
                return;
            }
            Path jsonlPath = Paths.get(agentProperties.getFineTune().getUploadDir(), datasetId + ".jsonl");
            Files.createDirectories(jsonlPath.getParent());
            int totalSamples = 0;
            try (BufferedWriter writer = Files.newBufferedWriter(jsonlPath, StandardCharsets.UTF_8)) {
                for (DocumentChunk chunk : chunks) {
                    try {
                        List<String> lines = generateQaLines(chunk.getContent(), qaPerChunk, systemText);
                        for (String line : lines) {
                            writer.write(line);
                            writer.newLine();
                        }
                        totalSamples += lines.size();
                    } catch (Exception e) {
                        log.warn("单切片生成 Q&A 失败，跳过 chunkId={}", chunk.getId(), e);
                    }
                }
            }
            if (totalSamples == 0) {
                markFailed(datasetId, "所有切片生成问答对均失败");
                return;
            }
            FineTuneDataset update = new FineTuneDataset();
            update.setId(datasetId);
            update.setFilePath(jsonlPath.toString());
            update.setSampleCount(totalSamples);
            update.setStatus(DatasetStatus.READY.name());
            fineTuneDatasetMapper.update(update);
            log.info("数据集生成完成 datasetId={} samples={}", datasetId, totalSamples);
        } catch (Exception e) {
            log.error("数据集生成失败 datasetId={}", datasetId, e);
            markFailed(datasetId, truncate(e.getMessage(), 500));
        }
    }

    /**
     * 查 KB 下所有 COMPLETED 文档的 chunk（chunk 无状态列，以所属文档状态为准）
     */
    private List<DocumentChunk> listCompletedChunks(Long kbId) {
        List<Long> docIds = knowledgeDocumentMapper.selectListByQuery(
                        QueryWrapper.create()
                                .select(KnowledgeDocument::getId)
                                .where(KnowledgeDocument::getKbId).eq(kbId)
                                .and(KnowledgeDocument::getStatus).eq(DocStatus.COMPLETED.name()))
                .stream().map(KnowledgeDocument::getId).toList();
        if (docIds.isEmpty()) {
            return List.of();
        }
        return documentChunkMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(DocumentChunk::getDocId).in(docIds)
                        .orderBy(DocumentChunk::getDocId, true)
                        .orderBy(DocumentChunk::getChunkIndex, true));
    }

    /**
     * 调 LLM 基于 chunk 生成 Q&A 对，每个 Q&A 转换为一行 ChatML JSON
     */
    private List<String> generateQaLines(String chunkContent, int qaCount, String systemText) {
        String userText = String.format(
                "请基于以下资料，生成 %d 个问答对。要求：\n"
                        + "1. 问题应覆盖资料中的关键知识点\n"
                        + "2. 回答应准确、完整、专业\n"
                        + "3. 仅以 JSON 数组格式输出，格式为 [{\"question\":\"...\",\"answer\":\"...\"}]\n"
                        + "4. 不要输出其他解释性文字\n\n资料：\n%s",
                qaCount, chunkContent);
        List<Message> messages = List.of(new SystemMessage(systemText), new UserMessage(userText));
        ChatResponse response = chatModel.call(new Prompt(messages));
        String rawOutput = response.getResult() != null && response.getResult().getOutput() != null
                ? response.getResult().getOutput().getText() : "[]";

        List<Map> qaList = JSONUtil.toList(LlmJsonExtractor.extractArray(rawOutput), Map.class);
        return qaList.stream()
                .filter(qa -> qa.get("question") != null && qa.get("answer") != null)
                .map(qa -> JSONUtil.toJsonStr(Map.of("messages", List.of(
                        Map.of("role", "system", "content", systemText),
                        Map.of("role", "user", "content", String.valueOf(qa.get("question"))),
                        Map.of("role", "assistant", "content", String.valueOf(qa.get("answer")))))))
                .toList();
    }

    private void markFailed(Long datasetId, String errorMessage) {
        FineTuneDataset update = new FineTuneDataset();
        update.setId(datasetId);
        update.setStatus(DatasetStatus.FAILED.name());
        update.setErrorMessage(truncate(errorMessage, 500));
        fineTuneDatasetMapper.update(update);
    }

    private static String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        return message.length() > maxLength ? message.substring(0, maxLength) : message;
    }

    private FineTuneDatasetVO toVO(FineTuneDataset dataset) {
        return FineTuneDatasetVO.builder()
                .id(dataset.getId())
                .kbId(dataset.getKbId())
                .name(dataset.getName())
                .description(dataset.getDescription())
                .format(dataset.getFormat())
                .sampleCount(dataset.getSampleCount())
                .status(dataset.getStatus())
                .errorMessage(dataset.getErrorMessage())
                .createTime(dataset.getCreateTime())
                .updateTime(dataset.getUpdateTime())
                .build();
    }
}
