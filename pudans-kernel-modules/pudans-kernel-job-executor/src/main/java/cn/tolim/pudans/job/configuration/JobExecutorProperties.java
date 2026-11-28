package cn.tolim.pudans.job.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.InetAddress;
import java.time.ZoneId;

// Configuration for the job executor, bound from pudans.job.executor.*
@ConfigurationProperties(prefix = JobExecutorProperties.DEFAULT_PREFIX)
public class JobExecutorProperties {

    public static final String DEFAULT_PREFIX = "pudans.job.executor";

    // Master switch for scheduling and execution
    private boolean enabled = true;

    // Whether enabled definitions are scheduled when the application is ready
    private boolean autoStart = true;

    // Identifier reported in execution records
    private String instanceName = defaultInstanceName();

    // Zone used to evaluate cron expressions
    private String zone = "Asia/Shanghai";

    // Threads used to fire trigger callbacks and timeouts
    private int schedulerPoolSize = 4;

    // Core worker threads running job handlers
    private int workerCorePoolSize = 4;

    // Maximum worker threads running job handlers
    private int workerMaxPoolSize = 16;

    // Queue capacity of the worker pool
    private int workerQueueCapacity = 200;

    // Timeout used when a definition does not specify one
    private int defaultTimeoutSeconds = 300;

    // Seconds to wait for running jobs during shutdown
    private int shutdownAwaitSeconds = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public int getSchedulerPoolSize() {
        return schedulerPoolSize;
    }

    public void setSchedulerPoolSize(int schedulerPoolSize) {
        this.schedulerPoolSize = schedulerPoolSize;
    }

    public int getWorkerCorePoolSize() {
        return workerCorePoolSize;
    }

    public void setWorkerCorePoolSize(int workerCorePoolSize) {
        this.workerCorePoolSize = workerCorePoolSize;
    }

    public int getWorkerMaxPoolSize() {
        return workerMaxPoolSize;
    }

    public void setWorkerMaxPoolSize(int workerMaxPoolSize) {
        this.workerMaxPoolSize = workerMaxPoolSize;
    }

    public int getWorkerQueueCapacity() {
        return workerQueueCapacity;
    }

    public void setWorkerQueueCapacity(int workerQueueCapacity) {
        this.workerQueueCapacity = workerQueueCapacity;
    }

    public int getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    public int getShutdownAwaitSeconds() {
        return shutdownAwaitSeconds;
    }

    public void setShutdownAwaitSeconds(int shutdownAwaitSeconds) {
        this.shutdownAwaitSeconds = shutdownAwaitSeconds;
    }

    // Resolved zone for cron evaluation
    public ZoneId zoneId() {
        return ZoneId.of(zone == null || zone.isBlank() ? "Asia/Shanghai" : zone);
    }

    private static String defaultInstanceName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "pudans-job-executor-" + ProcessHandle.current().pid();
        }
    }
}