package com.ai.aijava.agent.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ai.aijava.agent.config.AgentProperties;
import com.ai.aijava.agent.dto.request.CreateFineTuneJobRequest;
import com.ai.aijava.agent.dto.vo.FineTuneJobVO;
import com.ai.aijava.agent.entity.FineTuneDataset;
import com.ai.aijava.agent.entity.FineTuneJob;
import com.ai.aijava.agent.entity.KnowledgeBase;
import com.ai.aijava.agent.enums.DatasetStatus;
import com.ai.aijava.agent.enums.JobStatus;
import com.ai.aijava.agent.mapper.FineTuneDatasetMapper;
import com.ai.aijava.agent.mapper.FineTuneJobMapper;
import com.ai.aijava.agent.mapper.KnowledgeBaseMapper;
import com.ai.aijava.context.UserContext;
import com.ai.aijava.exception.ErrorCode;
import com.ai.aijava.exception.ThrowUtils;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.util.UpdateEntity;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 风格任务服务：从数据集 JSONL 蒸馏「风格规范 System Prompt」并回填 knowledge_base.style_prompt
 * 已替代原智谱官方微调（上传/训练/轮询），全流程本地完成，不依赖智谱微调 API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FineTuneJobService {

    private final FineTuneJobMapper fineTuneJobMapper;
    private final FineTuneDatasetMapper fineTuneDatasetMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ChatModel chatModel;
    private final AgentProperties agentProperties;

    /** 自注入代理：@Async 自调用失效，必须经代理 */
    @Resource
    @Lazy
    private FineTuneJobService self;

    /**
     * 创建风格任务（同步建记录 SUBMITTING，立即返回，异步蒸馏风格）
     */
    public FineTuneJobVO create(CreateFineTuneJobRequest request) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        FineTuneDataset dataset = fineTuneDatasetMapper.selectOneById(request.getDatasetId());
        ThrowUtils.throwIf(dataset == null, ErrorCode.NOT_FOUND_ERROR, "数据集不存在");
        ThrowUtils.throwIf(!DatasetStatus.READY.name().equals(dataset.getStatus()),
                ErrorCode.PARAMS_ERROR, "数据集尚未就绪，无法创建风格任务");
        // 归属校验：数据集所属 KB 必须属于当前用户
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(dataset.getKbId());
        ThrowUtils.throwIf(kb == null || !kb.getUserId().equals(userId),
                ErrorCode.NOT_FOUND_ERROR, "数据集不存在");

        int sampleLimit = request.getSampleLimit() != null
                ? request.getSampleLimit() : agentProperties.getFineTune().getStyleSampleLimit();

        LocalDateTime now = LocalDateTime.now();
        FineTuneJob job = FineTuneJob.builder()
                .datasetId(dataset.getId())
                .status(JobStatus.SUBMITTING.name())
                .hyperparams(JSONUtil.toJsonStr(Map.of("sampleLimit", sampleLimit)))
                .progress(0)
                .createTime(now)
                .updateTime(now)
                .build();
        fineTuneJobMapper.insert(job);

        self.distillStyleAsync(job.getId());
        return toVO(job);
    }

    /**
     * 数据集下任务列表（归属校验）
     */
    public List<FineTuneJobVO> listByDataset(Long datasetId) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        FineTuneDataset dataset = fineTuneDatasetMapper.selectOneById(datasetId);
        ThrowUtils.throwIf(dataset == null, ErrorCode.NOT_FOUND_ERROR, "数据集不存在");
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(dataset.getKbId());
        ThrowUtils.throwIf(kb == null || !kb.getUserId().equals(userId),
                ErrorCode.NOT_FOUND_ERROR, "数据集不存在");
        return fineTuneJobMapper.selectListByQuery(
                        QueryWrapper.create()
                                .where(FineTuneJob::getDatasetId).eq(datasetId)
                                .orderBy(FineTuneJob::getCreateTime, false))
                .stream().map(this::toVO).toList();
    }

    /**
     * 取消风格任务（仅本地状态取消，蒸馏全程在本地执行，无远端任务可取消）
     */
    public void cancel(Long jobId) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        FineTuneJob job = getOwnedJob(jobId, userId);
        ThrowUtils.throwIf(!JobStatus.SUBMITTING.name().equals(job.getStatus())
                        && !JobStatus.TRAINING.name().equals(job.getStatus()),
                ErrorCode.PARAMS_ERROR, "任务已结束，无法取消");
        FineTuneJob update = new FineTuneJob();
        update.setId(jobId);
        update.setStatus(JobStatus.CANCELLED.name());
        fineTuneJobMapper.update(update);
    }

    /**
     * 删除任务记录（进行中的任务不可删除）
     */
    public void delete(Long jobId) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        FineTuneJob job = getOwnedJob(jobId, userId);
        ThrowUtils.throwIf(JobStatus.TRAINING.name().equals(job.getStatus())
                        || JobStatus.SUBMITTING.name().equals(job.getStatus()),
                ErrorCode.PARAMS_ERROR, "进行中的任务无法删除，请先取消");
        fineTuneJobMapper.deleteById(jobId);
    }

    /**
     * 异步蒸馏：读 JSONL 抽样 → LLM 提炼风格规范 → 置 SUCCEEDED 并回填 KB style_prompt
     */
    @Async("fineTuneTaskExecutor")
    public void distillStyleAsync(Long jobId) {
        FineTuneJob job = fineTuneJobMapper.selectOneById(jobId);
        if (job == null) {
            return;
        }
        try {
            FineTuneDataset dataset = fineTuneDatasetMapper.selectOneById(job.getDatasetId());
            ThrowUtils.throwIf(dataset == null, ErrorCode.NOT_FOUND_ERROR, "数据集不存在");
            Path path = Path.of(dataset.getFilePath());
            ThrowUtils.throwIf(!Files.exists(path), ErrorCode.PARAMS_ERROR, "数据集文件不存在");

            List<String> samples = loadSamples(path, parseSampleLimit(job.getHyperparams()));
            ThrowUtils.throwIf(samples.isEmpty(), ErrorCode.PARAMS_ERROR, "数据集中没有可用样本");

            String stylePrompt = distillStylePrompt(samples);

            // 蒸馏期间用户可能已取消：落库前复核状态
            FineTuneJob latest = fineTuneJobMapper.selectOneById(jobId);
            if (latest != null && JobStatus.CANCELLED.name().equals(latest.getStatus())) {
                log.info("风格任务已取消，跳过结果回填 jobId={}", jobId);
                return;
            }
            FineTuneJob update = new FineTuneJob();
            update.setId(jobId);
            update.setStatus(JobStatus.SUCCEEDED.name());
            update.setProgress(100);
            update.setUpdateTime(LocalDateTime.now());
            fineTuneJobMapper.update(update);

            KnowledgeBase kbUpdate = UpdateEntity.of(KnowledgeBase.class);
            kbUpdate.setId(dataset.getKbId());
            kbUpdate.setStylePrompt(stylePrompt);
            knowledgeBaseMapper.update(kbUpdate);
            log.info("风格蒸馏完成 jobId={} kbId={} samples={}", jobId, dataset.getKbId(), samples.size());
        } catch (Exception e) {
            log.error("风格蒸馏失败 jobId={}", jobId, e);
            markJobFailed(jobId, truncate(e.getMessage(), 500));
        }
    }

    /**
     * 读取 JSONL 并抽样：样本数超过 sampleLimit 时等距抽样；单条样本超长按 styleMaxLength 截断
     */
    private List<String> loadSamples(Path jsonlPath, int sampleLimit) throws Exception {
        List<String> lines = Files.readAllLines(jsonlPath, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        List<String> picked = new ArrayList<>();
        if (lines.size() <= sampleLimit) {
            picked.addAll(lines);
        } else {
            double step = (double) lines.size() / sampleLimit;
            for (int i = 0; i < sampleLimit; i++) {
                picked.add(lines.get((int) (i * step)));
            }
        }
        int maxLength = agentProperties.getFineTune().getStyleMaxLength();
        List<String> samples = new ArrayList<>();
        for (String line : picked) {
            String qaText = extractQaText(line);
            if (qaText != null) {
                samples.add(truncate(qaText, maxLength));
            }
        }
        return samples;
    }

    /**
     * 从一行 ChatML JSON 中提取「问/答」文本（单行损坏时返回 null 跳过）
     */
    private static String extractQaText(String jsonlLine) {
        try {
            JSONArray messages = JSONUtil.parseObj(jsonlLine).getJSONArray("messages");
            if (messages == null) {
                return null;
            }
            String question = null;
            String answer = null;
            for (Object obj : messages) {
                JSONObject msg = (JSONObject) obj;
                String role = msg.getStr("role");
                if ("user".equals(role)) {
                    question = msg.getStr("content");
                } else if ("assistant".equals(role)) {
                    answer = msg.getStr("content");
                }
            }
            if (question == null || answer == null) {
                return null;
            }
            return "问：" + question + "\n答：" + answer;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 调 LLM 从样本蒸馏风格规范：只提炼口吻/结构/术语/禁忌，不含具体知识事实
     */
    private String distillStylePrompt(List<String> samples) {
        StringBuilder sampleBlock = new StringBuilder();
        for (int i = 0; i < samples.size(); i++) {
            sampleBlock.append("【样本 ").append(i + 1).append("】\n").append(samples.get(i)).append("\n\n");
        }
        String userText = String.format("""
                以下是某问答助手的历史问答样本（共 %d 条，单条可能被截断）：

                %s
                请从这些样本中蒸馏出一段「风格规范 System Prompt」，用于约束对话模型的回答风格。要求：
                1. 只描述回答风格：口吻与语气、回答结构（是否分点、是否先结论后展开）、术语与表达习惯、篇幅偏好、禁忌与边界
                2. 不要包含样本中的任何具体知识、事实或数据
                3. 直接以“你”开头输出规范正文（如“你是一名……”），不超过 500 字，不要输出任何额外解释
                """, samples.size(), sampleBlock);
        List<Message> messages = List.of(
                new SystemMessage("你是一名资深的语言风格分析专家，擅长从问答样本中归纳可复用的回答风格规范。"),
                new UserMessage(userText));
        ChatResponse response = chatModel.call(new Prompt(messages));
        String stylePrompt = response.getResult() != null && response.getResult().getOutput() != null
                ? response.getResult().getOutput().getText() : null;
        ThrowUtils.throwIf(stylePrompt == null || stylePrompt.isBlank(),
                ErrorCode.SYSTEM_ERROR, "LLM 未返回风格规范");
        return stylePrompt.trim();
    }

    /** 从 hyperparams JSON 解析抽样条数，缺省回退配置默认值 */
    private int parseSampleLimit(String hyperparams) {
        int defaultLimit = agentProperties.getFineTune().getStyleSampleLimit();
        if (hyperparams == null || hyperparams.isBlank()) {
            return defaultLimit;
        }
        try {
            Integer limit = JSONUtil.parseObj(hyperparams).getInt("sampleLimit");
            return limit != null && limit > 0 ? limit : defaultLimit;
        } catch (Exception e) {
            return defaultLimit;
        }
    }

    private FineTuneJob getOwnedJob(Long jobId, Long userId) {
        FineTuneJob job = fineTuneJobMapper.selectOneById(jobId);
        ThrowUtils.throwIf(job == null, ErrorCode.NOT_FOUND_ERROR, "风格任务不存在");
        FineTuneDataset dataset = fineTuneDatasetMapper.selectOneById(job.getDatasetId());
        KnowledgeBase kb = dataset != null ? knowledgeBaseMapper.selectOneById(dataset.getKbId()) : null;
        ThrowUtils.throwIf(kb == null || !kb.getUserId().equals(userId),
                ErrorCode.NOT_FOUND_ERROR, "风格任务不存在");
        return job;
    }

    private void markJobFailed(Long jobId, String errorMessage) {
        FineTuneJob update = new FineTuneJob();
        update.setId(jobId);
        update.setStatus(JobStatus.FAILED.name());
        update.setErrorMessage(truncate(errorMessage, 500));
        fineTuneJobMapper.update(update);
    }

    private static String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        return message.length() > maxLength ? message.substring(0, maxLength) : message;
    }

    private FineTuneJobVO toVO(FineTuneJob job) {
        return FineTuneJobVO.builder()
                .id(job.getId())
                .datasetId(job.getDatasetId())
                .baseModel(job.getBaseModel())
                .modelName(job.getModelName())
                .zhipuJobId(job.getZhipuJobId())
                .zhipuModelId(job.getZhipuModelId())
                .status(job.getStatus())
                .errorMessage(job.getErrorMessage())
                .progress(job.getProgress())
                .createTime(job.getCreateTime())
                .updateTime(job.getUpdateTime())
                .build();
    }
}
