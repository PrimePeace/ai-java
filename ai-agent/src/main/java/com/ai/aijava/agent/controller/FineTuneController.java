package com.ai.aijava.agent.controller;

import com.ai.aijava.agent.dto.request.CreateFineTuneJobRequest;
import com.ai.aijava.agent.dto.request.EvaluateQuestionRequest;
import com.ai.aijava.agent.dto.request.GenerateDatasetRequest;
import com.ai.aijava.agent.dto.vo.EvaluationRecordVO;
import com.ai.aijava.agent.dto.vo.FineTuneDatasetVO;
import com.ai.aijava.agent.dto.vo.FineTuneJobVO;
import com.ai.aijava.agent.service.DatasetGenerationService;
import com.ai.aijava.agent.service.EvaluationService;
import com.ai.aijava.agent.service.FineTuneJobService;
import com.ai.aijava.annotation.RequireLogin;
import com.ai.aijava.annotation.RequirePermission;
import com.ai.aijava.auth.Permissions;
import com.ai.aijava.common.BaseResponse;
import com.ai.aijava.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 风格蒸馏接口：数据集生成 / 风格任务管理 / 评测对比
 * （路由前缀保留 /fine-tune 以兼容前端；官方微调已替换为本地风格蒸馏）
 */
@Tag(name = "风格蒸馏")
@RequirePermission(Permissions.FT_USE)
@RestController
@RequestMapping("/fine-tune")
@RequiredArgsConstructor
public class FineTuneController {

    private final DatasetGenerationService datasetGenerationService;
    private final FineTuneJobService fineTuneJobService;
    private final EvaluationService evaluationService;

    // ========== 数据集 ==========

    @Operation(summary = "生成训练集")
    @RequireLogin
    @PostMapping("/dataset/generate")
    public BaseResponse<FineTuneDatasetVO> generateDataset(@RequestBody @Valid GenerateDatasetRequest request) {
        return ResultUtils.success(datasetGenerationService.generate(request));
    }

    @Operation(summary = "KB 下数据集列表")
    @RequireLogin
    @GetMapping("/dataset/list/{kbId}")
    public BaseResponse<List<FineTuneDatasetVO>> listDatasets(@PathVariable Long kbId) {
        return ResultUtils.success(datasetGenerationService.listMine(kbId));
    }

    @Operation(summary = "删除数据集")
    @RequireLogin
    @DeleteMapping("/dataset/{id}")
    public BaseResponse<Void> deleteDataset(@PathVariable Long id) {
        datasetGenerationService.delete(id);
        return ResultUtils.success(null);
    }

    // ========== 风格任务 ==========

    @Operation(summary = "创建风格任务（从数据集蒸馏风格规范）")
    @RequireLogin
    @PostMapping("/job/create")
    public BaseResponse<FineTuneJobVO> createJob(@RequestBody @Valid CreateFineTuneJobRequest request) {
        return ResultUtils.success(fineTuneJobService.create(request));
    }

    @Operation(summary = "数据集下风格任务列表")
    @RequireLogin
    @GetMapping("/job/list/{datasetId}")
    public BaseResponse<List<FineTuneJobVO>> listJobs(@PathVariable Long datasetId) {
        return ResultUtils.success(fineTuneJobService.listByDataset(datasetId));
    }

    @Operation(summary = "取消风格任务")
    @RequireLogin
    @PostMapping("/job/{id}/cancel")
    public BaseResponse<Void> cancelJob(@PathVariable Long id) {
        fineTuneJobService.cancel(id);
        return ResultUtils.success(null);
    }

    @Operation(summary = "删除风格任务")
    @RequireLogin
    @DeleteMapping("/job/{id}")
    public BaseResponse<Void> deleteJob(@PathVariable Long id) {
        fineTuneJobService.delete(id);
        return ResultUtils.success(null);
    }

    // ========== 评测 ==========

    @Operation(summary = "发起评测（批量问题，为空则从历史对话抽取）")
    @RequireLogin
    @PostMapping("/evaluation/run/{kbId}")
    public BaseResponse<List<EvaluationRecordVO>> runEvaluation(
            @PathVariable Long kbId,
            @RequestBody @Valid EvaluateQuestionRequest request) {
        request.setKbId(kbId);
        return ResultUtils.success(evaluationService.run(request));
    }

    @Operation(summary = "KB 下评测记录列表")
    @RequireLogin
    @GetMapping("/evaluation/list/{kbId}")
    public BaseResponse<List<EvaluationRecordVO>> listEvaluations(@PathVariable Long kbId) {
        return ResultUtils.success(evaluationService.list(kbId));
    }

    @Operation(summary = "手动评分")
    @RequireLogin
    @PostMapping("/evaluation/score/{id}")
    public BaseResponse<Void> scoreEvaluation(
            @PathVariable Long id,
            @RequestParam(required = false) Integer ragScore,
            @RequestParam(required = false) Integer styleScore) {
        evaluationService.score(id, ragScore, styleScore);
        return ResultUtils.success(null);
    }

    @Operation(summary = "评测汇总（平均分/胜率）")
    @RequireLogin
    @GetMapping("/evaluation/summary/{kbId}")
    public BaseResponse<Map<String, Object>> evaluationSummary(@PathVariable Long kbId) {
        return ResultUtils.success(evaluationService.summary(kbId));
    }
}
