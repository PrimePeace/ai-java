package com.ai.aijava.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 微调模块配置：异步线程池（风格蒸馏为纯本地任务，已移除智谱状态轮询调度）
 */
@Configuration
public class FineTuneConfig {

    /**
     * 微调异步任务线程池（数据集生成、风格蒸馏、评测执行）
     * 队列满退化为同步执行（CallerRuns），不丢任务
     */
    @Bean("fineTuneTaskExecutor")
    public Executor fineTuneTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("fine-tune-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
