package com.mkp.post.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 帖子索引同步线程池。
 *
 * 索引写入是旁路逻辑，单独一个线程池，避免和业务线程互相影响；
 * 队列满时用 CallerRunsPolicy 退化成同步执行，宁可慢一点也不丢索引。
 */
@Configuration
public class PostSearchAsyncConfig {

    @Bean("postSearchExecutor")
    public Executor postSearchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("post-search-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
