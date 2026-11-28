package cn.tolim.pudans.job.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// Infrastructure beans for scheduling and running jobs
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JobExecutorProperties.class)
public class JobExecutorConfiguration {

    // Dedicated scheduler used for cron triggers, fixed rate work and timeouts
    @Bean(name = "jobTaskScheduler", destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler jobTaskScheduler(JobExecutorProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(1, properties.getSchedulerPoolSize()));
        scheduler.setThreadNamePrefix("pudans-job-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    // Worker pool in which job handlers run
    @Bean(name = "jobExecutionExecutor")
    public ThreadPoolTaskExecutor jobExecutionExecutor(JobExecutorProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int corePoolSize = Math.max(1, properties.getWorkerCorePoolSize());
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(Math.max(corePoolSize, properties.getWorkerMaxPoolSize()));
        executor.setQueueCapacity(Math.max(0, properties.getWorkerQueueCapacity()));
        executor.setThreadNamePrefix("pudans-job-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(Math.max(0, properties.getShutdownAwaitSeconds()));
        return executor;
    }
}