package com.ai.aijava.agent.service;

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
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 微调任务服务：创建/取消/删除 + 智谱 API 提交 + 状态轮询回调
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FineTuneJobService {

    private final FineTuneJobMapper fineTuneJobMapper;
    private final FineTuneDatasetMapper fineTuneDatasetMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final ZhipuFineTuneClient zhipuClient;
    private final AgentProperties agentProperties;

    /** 自注入代理：@Async 自调用失效，必须经代理 */
    @Resource
    @Lazy
    private FineTuneJobService self;

    /**
     * 创建微调任务（同步建记录，异步上传数据集并提交智谱）
     */
    public FineTuneJobVO create(CreateFineTuneJobRequest request) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        FineTuneDataset dataset = fineTuneDatasetMapper.selectOneById(request.getDatasetId());
        ThrowUtils.throwIf(dataset == null, ErrorCode.NOT_FOUND_ERROR, "数据集不存在");
        ThrowUtils.throwIf(!DatasetStatus.READY.name().equals(dataset.getStatus()),
                ErrorCode.PARAMS_ERROR, "数据集尚未就绪，无法创建微调任务");
        // 归属校验：数据集所属 KB 必须属于当前用户
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(dataset.getKbId());
        ThrowUtils.throwIf(kb == null || !kb.getUserId().equals(userId),
                ErrorCode.NOT_FOUND_ERROR, "数据集不存在");

        Map<String, Object> hyperparams = new HashMap<>();
        hyperparams.put("n_epochs", request.getEpochs() != null ? request.getEpochs() : 3);
        hyperparams.put("batch_size", request.getBatchSize() != null ? request.getBatchSize() : 4);
        hyperparams.put("learning_rate_multiplier",
                request.getLearningRateMultiplier() != null ? request.getLearningRateMultiplier() : 1.0);

        // 未传基座时回退到配置默认（glm-4-flash，与资源包对齐）
        String baseModel = request.getBaseModel();
        if (baseModel == null || baseModel.isBlank()) {
            baseModel = agentProperties.getFineTune().getDefaultBaseModel();
        }

        LocalDateTime now = LocalDateTime.now();
        FineTuneJob job = FineTuneJob.builder()
                .datasetId(dataset.getId())
                .baseModel(baseModel)
                .modelName(request.getModelName())
                .zhipuJobId("")
                .zhipuModelId("")
                .status(JobStatus.SUBMITTING.name())
                .hyperparams(JSONUtil.toJsonStr(hyperparams))
                .progress(0)
                .createTime(now)
                .updateTime(now)
                .build();
        fineTuneJobMapper.insert(job);

        self.submitToZhipuAsync(job.getId());
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
     * 取消训练任务（仅进行中的任务可取消）
     */
    public void cancel(Long jobId) {
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        FineTuneJob job = getOwnedJob(jobId, userId);
        ThrowUtils.throwIf(!JobStatus.SUBMITTING.name().equals(job.getStatus())
                        && !JobStatus.TRAINING.name().equals(job.getStatus()),
                ErrorCode.PARAMS_ERROR, "任务已结束，无法取消");
        zhipuClient.cancelJob(job.getZhipuJobId());
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
                ErrorCode.PARAMS_ERROR, "训练中的任务无法删除，请先取消");
        fineTuneJobMapper.deleteById(jobId);
    }

    /**
     * 异步提交：上传 JSONL → 创建智谱微调任务 → 回填 zhipuJobId
     */
    @Async("fineTuneTaskExecutor")
    public void submitToZhipuAsync(Long jobId) {
        FineTuneJob job = fineTuneJobMapper.selectOneById(jobId);
        if (job == null) {
            return;
        }
        try {
            FineTuneDataset dataset = fineTuneDatasetMapper.selectOneById(job.getDatasetId());
            ThrowUtils.throwIf(dataset == null, ErrorCode.NOT_FOUND_ERROR, "数据集不存在");
            Path path = Path.of(dataset.getFilePath());
            ThrowUtils.throwIf(!Files.exists(path), ErrorCode.PARAMS_ERROR, "数据集文件不存在");

            String fileId = zhipuClient.uploadFile(path);
            Map<String, Object> hyperparams = JSONUtil.toBean(job.getHyperparams(), Map.class);
            String zhipuJobId = zhipuClient.createJob(job.getBaseModel(), fileId, job.getModelName(), hyperparams);

            FineTuneJob update = new FineTuneJob();
            update.setId(jobId);
            update.setZhipuJobId(zhipuJobId);
            update.setStatus(JobStatus.TRAINING.name());
            update.setProgress(10);
            fineTuneJobMapper.update(update);
            log.info("微调任务已提交 jobId={} zhipuJobId={}", jobId, zhipuJobId);
        } catch (Exception e) {
            log.error("提交微调任务失败 jobId={}", jobId, e);
            markJobFailed(jobId, truncate(e.getMessage(), 500));
        }
    }

    /**
     * 轮询回调：由 FineTuneJobPoller 定时触发，同步智谱任务状态到本地
     * 智谱状态：validating_files/queued/running/succeeded/failed/cancelled（无进度字段，按状态映射）
     */
    public void pollJobStatus(Long jobId) {
        FineTuneJob job = fineTuneJobMapper.selectOneById(jobId);
        if (job == null || job.getZhipuJobId() == null || job.getZhipuJobId().isBlank()) {
            return;
        }
        if (!JobStatus.SUBMITTING.name().equals(job.getStatus())
                && !JobStatus.TRAINING.name().equals(job.getStatus())) {
            return;
        }
        try {
            Map<String, Object> info = zhipuClient.queryJob(job.getZhipuJobId());
            String zhipuStatus = String.valueOf(info.getOrDefault("status", ""));
            FineTuneJob update = new FineTuneJob();
            update.setId(jobId);
            switch (zhipuStatus) {
                case "validating_files" -> {
                    update.setStatus(JobStatus.TRAINING.name());
                    update.setProgress(5);
                }
                case "queued" -> {
                    update.setStatus(JobStatus.TRAINING.name());
                    update.setProgress(10);
                }
                case "running" -> {
                    update.setStatus(JobStatus.TRAINING.name());
                    update.setProgress(50);
                }
                case "succeeded" -> {
                    update.setStatus(JobStatus.SUCCEEDED.name());
                    update.setProgress(100);
                    String ftModel = (String) info.get("fine_tuned_model");
                    update.setZhipuModelId(ftModel);
                    autoBindModelToKb(job, ftModel);
                }
                case "failed" -> {
                    update.setStatus(JobStatus.FAILED.name());
                    update.setErrorMessage(extractError(info));
                }
                case "cancelled" -> update.setStatus(JobStatus.CANCELLED.name());
                default -> {
                    return;
                }
            }
            fineTuneJobMapper.update(update);
        } catch (Exception e) {
            log.warn("轮询微调任务状态失败 jobId={}", jobId, e);
        }
    }

    /**
     * 微调成功后自动回填 KB 的 ft_model_id，并将引擎切为 auto（有模型走微调，无则回退 RAG）
     */
    private void autoBindModelToKb(FineTuneJob job, String ftModelId) {
        if (ftModelId == null || ftModelId.isBlank()) {
            return;
        }
        FineTuneDataset dataset = fineTuneDatasetMapper.selectOneById(job.getDatasetId());
        if (dataset == null) {
            return;
        }
        KnowledgeBase kbUpdate = UpdateEntity.of(KnowledgeBase.class);
        kbUpdate.setId(dataset.getKbId());
        kbUpdate.setFtModelId(ftModelId);
        kbUpdate.setChatEngine("auto");
        knowledgeBaseMapper.update(kbUpdate);
        log.info("微调成功，自动绑定 KB kbId={} modelId={}", dataset.getKbId(), ftModelId);
    }

    @SuppressWarnings("unchecked")
    private static String extractError(Map<String, Object> info) {
        Object error = info.get("error");
        if (error instanceof Map<?, ?> errorMap && errorMap.get("message") != null) {
            return truncate(String.valueOf(errorMap.get("message")), 500);
        }
        return "训练失败（智谱未返回具体原因）";
    }

    private FineTuneJob getOwnedJob(Long jobId, Long userId) {
        FineTuneJob job = fineTuneJobMapper.selectOneById(jobId);
        ThrowUtils.throwIf(job == null, ErrorCode.NOT_FOUND_ERROR, "微调任务不存在");
        FineTuneDataset dataset = fineTuneDatasetMapper.selectOneById(job.getDatasetId());
        KnowledgeBase kb = dataset != null ? knowledgeBaseMapper.selectOneById(dataset.getKbId()) : null;
        ThrowUtils.throwIf(kb == null || !kb.getUserId().equals(userId),
                ErrorCode.NOT_FOUND_ERROR, "微调任务不存在");
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
