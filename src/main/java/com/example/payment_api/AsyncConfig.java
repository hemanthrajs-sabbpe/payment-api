package com.example.payment_api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration providing dedicated managed thread pools for background operations.
 */
@Configuration
public class AsyncConfig {

    /**
     * Dedicated Spring-managed TaskExecutor for transactional payment email notifications.
     * Prevents blocking application request threads and avoids common ForkJoinPool.
     */
    @Bean(name = "paymentEmailExecutor")
    public Executor paymentEmailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("PaymentEmail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
