package com.ai.aijava.agent.service;

import com.ai.aijava.agent.entity.FineTuneJob;
import com.ai.aijava.agent.enums.JobStatus;
import com.ai.aijava.agent.mapper.FineTuneJobMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 微调任务状态轮询器：定时同步智谱侧训练进度到本地 fine_tune_job 表
 * 只轮询进行中（SUBMITTING/TRAINING）的任务；无任务时一次空查询即返回
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FineTuneJobPoller {

    private final FineTuneJobMapper fineTuneJobMapper;
    private final FineTuneJobService fineTuneJobService;

    @Scheduled(initialDelay = 15000, fixedDelayString = "${agent.fine-tune.poll-interval-ms:30000}")
    public void pollRunningJobs() {
        List<FineTuneJob> runningJobs = fineTuneJobMapper.selectListByQuery(
                QueryWrapper.create()
                        .select(FineTuneJob::getId)
                        .where(FineTuneJob::getStatus).in(
                                JobStatus.SUBMITTING.name(), JobStatus.TRAINING.name()));
        if (runningJobs.isEmpty()) {
            return;
        }
        log.debug("轮询微调任务状态，进行中任务数={}", runningJobs.size());
        for (FineTuneJob job : runningJobs) {
            fineTuneJobService.pollJobStatus(job.getId());
        }
    }
}
