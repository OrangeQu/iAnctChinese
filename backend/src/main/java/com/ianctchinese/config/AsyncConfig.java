package com.ianctchinese.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步执行配置类
 * 用于支持API并行调用，提高分析速度
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 配置用于分析任务的线程池
     * - 核心线程数：4（适合IO密集型任务：LLM调用、数据库查询）
     * - 最大线程数：8
     * - 队列容量：100
     * - 线程名前缀：analysis-async-
     */
    @Bean(name = "analysisTaskExecutor")
    public Executor analysisTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("analysis-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
