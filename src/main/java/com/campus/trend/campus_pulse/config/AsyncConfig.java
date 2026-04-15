package com.campus.trend.campus_pulse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${campus.async.core-pool-size:4}")
    private int corePoolSize;

    @Value("${campus.async.max-pool-size:8}")
    private int maxPoolSize;

    @Value("${campus.async.queue-capacity:500}")
    private int queueCapacity;

    @Value("${campus.async.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Value("${campus.async.await-termination-seconds:60}")
    private int awaitTerminationSeconds;

    @Value("${campus.async.thread-name-prefix:campus-async-}")
    private String threadNamePrefix;

    /**
     * 主业务异步线程池
     * 4h4g 服务器：核心线程=CPU核数，最大=2x核数
     * 用于标签处理、经验发放、用户画像更新等
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(2, corePoolSize));
        executor.setMaxPoolSize(Math.max(executor.getCorePoolSize(), maxPoolSize));
        executor.setQueueCapacity(Math.max(100, queueCapacity));
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setKeepAliveSeconds(Math.max(30, keepAliveSeconds));
        // 队列满时由调用方线程执行（降级策略，不丢任务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(Math.max(30, awaitTerminationSeconds));
        executor.initialize();
        return executor;
    }

    /**
     * 通知/WS推送专用线程池
     * 与主业务池隔离，通知积压不影响核心业务
     * 队列满时丢弃最旧的任务（通知是非关键业务）
     */
    @Bean(name = "notifyExecutor")
    public Executor notifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("campus-notify-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
