package cn.tolim.pudans.job.service;

import cn.tolim.pudans.job.configuration.JobExecutorProperties;
import cn.tolim.pudans.job.dto.JobExecutionResponse;
import cn.tolim.pudans.job.entity.JobDefinitionEntity;
import cn.tolim.pudans.job.entity.JobExecutionEntity;
import cn.tolim.pudans.job.enums.BlockStrategy;
import cn.tolim.pudans.job.enums.ExecutionStatus;
import cn.tolim.pudans.job.enums.JobStatus;
import cn.tolim.pudans.job.enums.TriggerType;
import cn.tolim.pudans.job.handler.JobExecutionContext;
import cn.tolim.pudans.job.handler.JobHandler;
import cn.tolim.pudans.job.handler.JobHandlerRegistry;
import cn.tolim.pudans.job.handler.JobHandlerResult;
import cn.tolim.pudans.job.repository.JobDefinitionRepository;
import cn.tolim.pudans.job.repository.JobExecutionRepository;
import cn.tolim.pudans.job.support.JobErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

// Creates execution records and runs job handlers with timeout, retry and overlap control
@Service
public class JobExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobExecutionService.class);

    // Longer error messages are truncated before being stored
    private static final int MAX_MESSAGE_LENGTH = 4_000;

    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final JobHandlerRegistry jobHandlerRegistry;
    private final TaskExecutor taskExecutor;
    private final TaskScheduler taskScheduler;
    private final JobExecutorProperties properties;

    private final ConcurrentMap<String, ReentrantLock> jobLocks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Thread> runningThreads = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> executionStateLocks = new ConcurrentHashMap<>();
    private final Set<String> cancelRequested = ConcurrentHashMap.newKeySet();

    public JobExecutionService(JobDefinitionRepository jobDefinitionRepository,
                               JobExecutionRepository jobExecutionRepository,
                               JobHandlerRegistry jobHandlerRegistry,
                               @Qualifier("jobExecutionExecutor") TaskExecutor taskExecutor,
                               @Qualifier("jobTaskScheduler") TaskScheduler taskScheduler,
                               JobExecutorProperties properties) {
        this.jobDefinitionRepository = jobDefinitionRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.jobHandlerRegistry = jobHandlerRegistry;
        this.taskExecutor = taskExecutor;
        this.taskScheduler = taskScheduler;
        this.properties = properties;
    }

    // Accept a new execution, persist it as QUEUED and dispatch it to the worker pool
    public String trigger(JobDefinitionEntity job, TriggerType triggerType, String triggeredBy,
                          String params) {
        if (!properties.isEnabled()) {
            throw JobErrors.executorDisabled();
        }
        JobExecutionEntity execution = newExecution(job, triggerType, 1,
                resolveMaxRetries(job) + 1, null, triggeredBy, params);
        jobExecutionRepository.save(execution);
        submit(execution.getId());
        return execution.getId();
    }

    // Called by the scheduler when a job is due
    public void executeScheduled(String jobId) {
        if (!properties.isEnabled()) {
            return;
        }
        jobDefinitionRepository.findById(jobId)
                .filter(job -> job.getStatus() == JobStatus.ENABLED)
                .ifPresent(job -> {
                    try {
                        trigger(job, TriggerType.SCHEDULED, "scheduler", null);
                    } catch (RuntimeException ex) {
                        LOGGER.error("Failed to trigger scheduled job {}", job.getCode(), ex);
                    }
                });
    }

    // Run a queued execution in the calling thread, normally a worker pool thread
    public void executeExecution(String executionId) {
        try {
            runExecution(executionId);
        } finally {
            executionStateLocks.remove(executionId);
            cancelRequested.remove(executionId);
        }
    }

    private void runExecution(String executionId) {
        Object stateLock = stateLock(executionId);
        JobExecutionEntity execution;
        JobDefinitionEntity job;
        JobHandler handler;
        synchronized (stateLock) {
            execution = jobExecutionRepository.findById(executionId).orElse(null);
            if (execution == null || execution.getStatus() != ExecutionStatus.QUEUED) {
                return;
            }
            if (cancelRequested.remove(executionId)) {
                complete(execution, ExecutionStatus.CANCELED, "execution canceled before start",
                        null);
                return;
            }
            job = jobDefinitionRepository.findById(execution.getJobId()).orElse(null);
            if (job == null) {
                complete(execution, ExecutionStatus.FAILED, "job definition no longer exists", null);
                return;
            }
            Optional<JobHandler> resolved = jobHandlerRegistry.find(execution.getHandlerName());
            if (resolved.isEmpty()) {
                complete(execution, ExecutionStatus.FAILED,
                        "no handler registered: " + execution.getHandlerName(), null);
                return;
            }
            handler = resolved.get();
            markRunning(execution);
            // Register the worker before releasing the state lock so a concurrent cancel can interrupt it
            runningThreads.put(executionId, Thread.currentThread());
        }
        ReentrantLock lock = jobLocks.computeIfAbsent(job.getId(),
                key -> new ReentrantLock(true));
        boolean acquired = false;
        try {
            acquired = acquireLock(job, execution, lock);
            if (!acquired) {
                return;
            }
            AtomicBoolean timedOut = new AtomicBoolean(false);
            long timeoutSeconds = resolveTimeoutSeconds(job);
            ScheduledFuture<?> watchdog = scheduleWatchdog(executionId, timeoutSeconds, timedOut);
            try {
                JobHandlerResult result;
                try {
                    result = handler.execute(toContext(job, execution));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    if (timedOut.get()) {
                        complete(execution, ExecutionStatus.TIMEOUT,
                                timeoutMessage(timeoutSeconds), null);
                    } else {
                        complete(execution, ExecutionStatus.CANCELED, "execution canceled", null);
                    }
                    return;
                } catch (Exception ex) {
                    if (cancelRequested.remove(executionId)) {
                        complete(execution, ExecutionStatus.CANCELED, "execution canceled", ex);
                    } else {
                        complete(execution, ExecutionStatus.FAILED, describe(ex), ex);
                        scheduleRetry(job, execution);
                    }
                    return;
                }
                if (timedOut.get()) {
                    complete(execution, ExecutionStatus.TIMEOUT, timeoutMessage(timeoutSeconds),
                            null);
                } else if (cancelRequested.remove(executionId)) {
                    complete(execution, ExecutionStatus.CANCELED, "execution canceled", null);
                } else if (result != null && result.success()) {
                    complete(execution, ExecutionStatus.SUCCESS, result.message(), null);
                } else {
                    complete(execution, ExecutionStatus.FAILED,
                            result == null ? "handler returned no result" : result.message(), null);
                    scheduleRetry(job, execution);
                }
            } finally {
                if (watchdog != null) {
                    watchdog.cancel(false);
                }
                runningThreads.remove(executionId);
                // Clear any interrupt left over from a timeout or cancellation
                Thread.interrupted();
            }
        } finally {
            if (acquired) {
                lock.unlock();
            }
            runningThreads.remove(executionId);
        }
    }

    // Request cancellation of a queued or running execution
    public JobExecutionResponse cancel(String executionId) {
        Object stateLock = stateLock(executionId);
        synchronized (stateLock) {
            JobExecutionEntity execution = jobExecutionRepository.findById(executionId)
                    .orElseThrow(() -> JobErrors.executionNotFound(executionId));
            if (execution.getStatus() == ExecutionStatus.QUEUED) {
                complete(execution, ExecutionStatus.CANCELED, "execution canceled before start",
                        null);
                cancelRequested.remove(executionId);
                executionStateLocks.remove(executionId);
                return JobExecutionResponse.from(execution);
            }
            if (execution.getStatus() == ExecutionStatus.RUNNING) {
                cancelRequested.add(executionId);
                Thread thread = runningThreads.get(executionId);
                if (thread != null) {
                    thread.interrupt();
                }
                return JobExecutionResponse.from(execution, true);
            }
            throw JobErrors.executionNotCancelable(executionId);
        }
    }

    // Mark executions left behind by a previous run of this executor instance as failed
    public int recoverAbandonedExecutions() {
        List<JobExecutionEntity> abandoned = jobExecutionRepository
                .findByStatusInAndInstanceName(
                        List.of(ExecutionStatus.QUEUED, ExecutionStatus.RUNNING),
                        properties.getInstanceName());
        int recovered = 0;
        for (JobExecutionEntity execution : abandoned) {
            complete(execution, ExecutionStatus.FAILED,
                    "executor restarted while the execution was in progress", null);
            recovered++;
        }
        if (recovered > 0) {
            LOGGER.warn("Marked {} abandoned job executions as failed", recovered);
        }
        return recovered;
    }

    // Whether a cancellation was requested for the execution
    public boolean isCancelRequested(String executionId) {
        return cancelRequested.contains(executionId);
    }

    private void submit(String executionId) {
        try {
            taskExecutor.execute(() -> {
                try {
                    executeExecution(executionId);
                } catch (RuntimeException ex) {
                    LOGGER.error("Unexpected error while running job execution {}", executionId, ex);
                    jobExecutionRepository.findById(executionId).ifPresent(execution -> {
                        if (!execution.getStatus().isTerminal()) {
                            complete(execution, ExecutionStatus.FAILED,
                                    "unexpected executor error: " + describe(ex), ex);
                        }
                    });
                }
            });
        } catch (RejectedExecutionException ex) {
            jobExecutionRepository.findById(executionId).ifPresent(execution ->
                    complete(execution, ExecutionStatus.FAILED,
                            "executor rejected the task: " + describe(ex), ex));
        }
    }

    private JobExecutionEntity newExecution(JobDefinitionEntity job, TriggerType triggerType,
                                            int attempt, int maxAttempts,
                                            String retryOfExecutionId, String triggeredBy,
                                            String params) {
        LocalDateTime now = LocalDateTime.now();
        JobExecutionEntity execution = new JobExecutionEntity();
        execution.setId(UUID.randomUUID().toString());
        execution.setJobId(job.getId());
        execution.setJobCode(job.getCode());
        execution.setJobName(job.getName());
        execution.setHandlerName(job.getHandlerName());
        execution.setTriggerType(triggerType);
        execution.setStatus(ExecutionStatus.QUEUED);
        execution.setAttempt(attempt);
        execution.setMaxAttempts(maxAttempts);
        execution.setScheduledTime(now);
        execution.setInstanceName(properties.getInstanceName());
        execution.setParams(params != null ? params : job.getParams());
        execution.setRetryOfExecutionId(retryOfExecutionId);
        String operator = triggeredBy == null || triggeredBy.isBlank() ? "system" : triggeredBy;
        execution.setTriggeredBy(operator);
        execution.setCreateBy(operator);
        execution.setUpdateBy(operator);
        return execution;
    }

    private JobExecutionContext toContext(JobDefinitionEntity job, JobExecutionEntity execution) {
        return new JobExecutionContext(execution.getId(), job.getId(), job.getCode(), job.getName(),
                job.getHandlerName(), execution.getParams(), execution.getTriggerType(),
                execution.getAttempt(), execution.getInstanceName(), execution.getScheduledTime());
    }

    private boolean acquireLock(JobDefinitionEntity job, JobExecutionEntity execution,
                                ReentrantLock lock) {
        BlockStrategy strategy = job.getBlockStrategy() == null
                ? BlockStrategy.SERIAL : job.getBlockStrategy();
        if (strategy == BlockStrategy.PARALLEL) {
            return true;
        }
        if (strategy == BlockStrategy.DISCARD) {
            if (lock.tryLock()) {
                return true;
            }
            complete(execution, ExecutionStatus.SKIPPED,
                    "previous execution of the job is still running", null);
            return false;
        }
        long waitSeconds = Math.max(0, resolveTimeoutSeconds(job));
        try {
            boolean locked = waitSeconds == 0 ? lock.tryLock()
                    : lock.tryLock(waitSeconds, TimeUnit.SECONDS);
            if (locked) {
                return true;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (cancelRequested.remove(execution.getId())) {
                complete(execution, ExecutionStatus.CANCELED, "execution canceled", null);
                return false;
            }
        }
        complete(execution, ExecutionStatus.SKIPPED,
                "timed out waiting for the previous execution to finish", null);
        return false;
    }

    private Object stateLock(String executionId) {
        return executionStateLocks.computeIfAbsent(executionId, key -> new Object());
    }

    private ScheduledFuture<?> scheduleWatchdog(String executionId, long timeoutSeconds,
                                                AtomicBoolean timedOut) {
        if (timeoutSeconds <= 0) {
            return null;
        }
        try {
            return taskScheduler.schedule(() -> {
                timedOut.set(true);
                Thread thread = runningThreads.get(executionId);
                if (thread != null) {
                    thread.interrupt();
                }
            }, Instant.now().plusSeconds(timeoutSeconds));
        } catch (RuntimeException ex) {
            LOGGER.warn("Unable to schedule timeout watchdog for execution {}", executionId, ex);
            return null;
        }
    }

    private void markRunning(JobExecutionEntity execution) {
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setStartTime(LocalDateTime.now());
        jobExecutionRepository.save(execution);
    }

    private void complete(JobExecutionEntity execution, ExecutionStatus status, String detail,
                          Throwable error) {
        LocalDateTime now = LocalDateTime.now();
        execution.setStatus(status);
        execution.setEndTime(now);
        LocalDateTime startedAt = execution.getStartTime() != null
                ? execution.getStartTime() : execution.getCreateTime();
        if (startedAt != null) {
            execution.setDurationMs(Duration.between(startedAt, now).toMillis());
        }
        if (status == ExecutionStatus.SUCCESS) {
            execution.setResult(truncate(detail));
        } else {
            execution.setErrorMessage(truncate(detail));
        }
        jobExecutionRepository.save(execution);
        if (error != null) {
            LOGGER.warn("Job execution {} of job {} finished with status {}: {}",
                    execution.getId(), execution.getJobCode(), status, detail, error);
        }
    }

    private void scheduleRetry(JobDefinitionEntity job, JobExecutionEntity failed) {
        int nextAttempt = failed.getAttempt() + 1;
        if (nextAttempt > failed.getMaxAttempts()) {
            return;
        }
        long delaySeconds = job.getRetryIntervalSeconds() == null
                ? 0 : Math.max(0, job.getRetryIntervalSeconds());
        Runnable retry = () -> {
            try {
                retryExecution(job.getId(), failed.getId());
            } catch (RuntimeException ex) {
                LOGGER.error("Failed to schedule retry for job {}", job.getCode(), ex);
            }
        };
        try {
            if (delaySeconds <= 0) {
                taskExecutor.execute(retry);
            } else {
                taskScheduler.schedule(retry, Instant.now().plusSeconds(delaySeconds));
            }
        } catch (RuntimeException ex) {
            LOGGER.error("Unable to schedule retry for job {}", job.getCode(), ex);
        }
    }

    private void retryExecution(String jobId, String previousExecutionId) {
        JobDefinitionEntity job = jobDefinitionRepository.findById(jobId).orElse(null);
        JobExecutionEntity previous = jobExecutionRepository.findById(previousExecutionId)
                .orElse(null);
        if (job == null || previous == null) {
            return;
        }
        int nextAttempt = previous.getAttempt() + 1;
        if (nextAttempt > previous.getMaxAttempts()) {
            return;
        }
        JobExecutionEntity execution = newExecution(job, TriggerType.RETRY, nextAttempt,
                previous.getMaxAttempts(), previous.getId(), previous.getTriggeredBy(),
                previous.getParams());
        jobExecutionRepository.save(execution);
        submit(execution.getId());
    }

    private int resolveMaxRetries(JobDefinitionEntity job) {
        return job.getMaxRetries() == null ? 0 : Math.max(0, job.getMaxRetries());
    }

    private long resolveTimeoutSeconds(JobDefinitionEntity job) {
        if (job.getTimeoutSeconds() != null) {
            return Math.max(0, job.getTimeoutSeconds());
        }
        return Math.max(0, properties.getDefaultTimeoutSeconds());
    }

    private String timeoutMessage(long timeoutSeconds) {
        return "execution timed out after " + timeoutSeconds + " seconds";
    }

    private String describe(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null || message.isBlank()
                ? "" : ": " + message);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_MESSAGE_LENGTH ? value
                : value.substring(0, MAX_MESSAGE_LENGTH);
    }
}