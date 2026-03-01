package com.campus.trend.campus_pulse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Song：异步任务配置
 * Song：用于邮件发送等异步操作
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Song：核心线程数
        executor.setCorePoolSize(5);

        // Song：最大线程数
        executor.setMaxPoolSize(10);

        // Song：队列容量
        executor.setQueueCapacity(100);

        // Song：线程名称前缀
        executor.setThreadNamePrefix("async-mail-");

        // Song：线程空闲时间（秒）
        executor.setKeepAliveSeconds(60);

        // Song：拒绝策略：由调用线程处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Song：等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Song：等待时间（秒）
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
