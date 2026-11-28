package cn.tolim.pudans.job.service;

import cn.tolim.pudans.communal.core.error.ApiException;
import cn.tolim.pudans.job.configuration.JobExecutorProperties;
import cn.tolim.pudans.job.entity.JobDefinitionEntity;
import cn.tolim.pudans.job.entity.JobExecutionEntity;
import cn.tolim.pudans.job.enums.BlockStrategy;
import cn.tolim.pudans.job.enums.ExecutionStatus;
import cn.tolim.pudans.job.enums.JobStatus;
import cn.tolim.pudans.job.enums.ScheduleType;
import cn.tolim.pudans.job.enums.TriggerType;
import cn.tolim.pudans.job.handler.JobExecutionContext;
import cn.tolim.pudans.job.handler.JobHandler;
import cn.tolim.pudans.job.handler.JobHandlerRegistry;
import cn.tolim.pudans.job.handler.JobHandlerResult;
import cn.tolim.pudans.job.repository.JobDefinitionRepository;
import cn.tolim.pudans.job.repository.JobExecutionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobExecutionServiceTests {

    private JobDefinitionRepository definitionRepository;
    private JobExecutionRepository executionRepository;
    private Map<String, JobDefinitionEntity> definitionStore;
    private Map<String, JobExecutionEntity> executionStore;
    private ThreadPoolTaskExecutor executor;
    private ThreadPoolTaskScheduler scheduler;
    private JobExecutorProperties properties;

    @BeforeEach
    void setUp() {
        definitionRepository = mock(JobDefinitionRepository.class);
        executionRepository = mock(JobExecutionRepository.class);
        definitionStore = new ConcurrentHashMap<>();
        executionStore = new ConcurrentHashMap<>();

        when(definitionRepository.findById(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        definitionStore.get(invocation.getArgument(0))));
        when(executionRepository.findById(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        executionStore.get(invocation.getArgument(0))));
        when(executionRepository.save(any(JobExecutionEntity.class)))
                .thenAnswer(invocation -> {
                    JobExecutionEntity entity = invocation.getArgument(0);
                    executionStore.put(entity.getId(), entity);
                    return entity;
                });

        properties = new JobExecutorProperties();
        properties.setInstanceName("test-instance");
        properties.setDefaultTimeoutSeconds(300);

        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.initialize();

        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.initialize();
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
        scheduler.shutdown();
    }

    @Test
    void recordsSuccessfulExecution() {
        JobExecutionService service = service(List.of(handler(() -> JobHandlerResult.ok("ok"))),
                null);
        JobDefinitionEntity job = job("job-1", BlockStrategy.SERIAL, 0, 0, 0);

        String executionId = service.trigger(job, TriggerType.MANUAL, "tester", "{\"a\":1}");

        JobExecutionEntity execution = executionStore.get(executionId);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(execution.getResult()).isEqualTo("ok");
        assertThat(execution.getErrorMessage()).isNull();
        assertThat(execution.getStartTime()).isNotNull();
        assertThat(execution.getEndTime()).isNotNull();
        assertThat(execution.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(execution.getAttempt()).isEqualTo(1);
        assertThat(execution.getMaxAttempts()).isEqualTo(1);
        assertThat(execution.getParams()).isEqualTo("{\"a\":1}");
        assertThat(execution.getTriggeredBy()).isEqualTo("tester");
        assertThat(execution.getInstanceName()).isEqualTo("test-instance");
    }

    @Test
    void recordsFailedExecutionWhenHandlerThrows() {
        JobHandler failing = new JobHandler() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public JobHandlerResult execute(JobExecutionContext context) {
                throw new IllegalStateException("boom");
            }
        };
        JobExecutionService service = service(List.of(failing), null);
        JobDefinitionEntity job = job("job-1", BlockStrategy.SERIAL, 0, 0, 0);

        String executionId = service.trigger(job, TriggerType.MANUAL, "tester", null);

        JobExecutionEntity execution = executionStore.get(executionId);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getErrorMessage()).contains("IllegalStateException").contains("boom");
        assertThat(execution.getEndTime()).isNotNull();
    }

    @Test
    void recordsFailedExecutionWhenHandlerIsMissing() {
        JobExecutionService service = service(List.of(), null);
        JobDefinitionEntity job = job("job-1", BlockStrategy.SERIAL, 0, 0, 0);

        String executionId = service.trigger(job, TriggerType.MANUAL, "tester", null);

        JobExecutionEntity execution = executionStore.get(executionId);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getErrorMessage()).contains("no handler registered");
    }

    @Test
    void schedulesRetryAfterFailure() {
        TaskScheduler retryScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(scheduled)
                .when(retryScheduler).schedule(any(Runnable.class), any(Instant.class));
        JobHandler failing = new JobHandler() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public JobHandlerResult execute(JobExecutionContext context) {
                throw new IllegalStateException("boom");
            }
        };
        JobExecutionService service = new JobExecutionService(definitionRepository,
                executionRepository, new JobHandlerRegistry(List.of(failing)),
                new SyncTaskExecutor(), retryScheduler, properties);
        JobDefinitionEntity job = job("job-1", BlockStrategy.SERIAL, 1, 5, 0);

        String firstExecutionId = service.trigger(job, TriggerType.MANUAL, "tester", null);

        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        verify(retryScheduler, times(1)).schedule(retry.capture(), any(Instant.class));
        retry.getValue().run();

        assertThat(executionStore).hasSize(2);
        JobExecutionEntity first = executionStore.get(firstExecutionId);
        assertThat(first.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(first.getAttempt()).isEqualTo(1);
        JobExecutionEntity second = executionStore.values().stream()
                .filter(entity -> !entity.getId().equals(firstExecutionId))
                .findFirst().orElseThrow();
        assertThat(second.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(second.getAttempt()).isEqualTo(2);
        assertThat(second.getMaxAttempts()).isEqualTo(2);
        assertThat(second.getTriggerType()).isEqualTo(TriggerType.RETRY);
        assertThat(second.getRetryOfExecutionId()).isEqualTo(firstExecutionId);
        verify(retryScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void timesOutLongRunningHandler() {
        JobHandler handler = new JobHandler() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public JobHandlerResult execute(JobExecutionContext context) throws Exception {
                Thread.sleep(5_000);
                return JobHandlerResult.ok();
            }
        };
        JobExecutionService service = service(List.of(handler), executor);
        JobDefinitionEntity job = job("job-1", BlockStrategy.SERIAL, 0, 0, 1);

        String executionId = service.trigger(job, TriggerType.SCHEDULED, "scheduler", null);

        awaitStatus(executionId, ExecutionStatus.TIMEOUT);
        assertThat(executionStore.get(executionId).getErrorMessage())
                .contains("timed out after 1 seconds");
    }

    @Test
    void cancelsRunningExecution() {
        JobHandler handler = new JobHandler() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public JobHandlerResult execute(JobExecutionContext context) throws Exception {
                Thread.sleep(5_000);
                return JobHandlerResult.ok();
            }
        };
        JobExecutionService service = service(List.of(handler), executor);
        JobDefinitionEntity job = job("job-1", BlockStrategy.SERIAL, 0, 0, 0);

        String executionId = service.trigger(job, TriggerType.MANUAL, "tester", null);
        awaitStatus(executionId, ExecutionStatus.RUNNING);

        service.cancel(executionId);

        awaitStatus(executionId, ExecutionStatus.CANCELED);
    }

    @Test
    void cancelsQueuedExecution() {
        JobExecutionService service = service(List.of(handler(JobHandlerResult::ok)), null);
        execution("exec-queued", ExecutionStatus.QUEUED);

        var response = service.cancel("exec-queued");

        assertThat(response.status()).isEqualTo(ExecutionStatus.CANCELED);
        assertThat(response.cancelRequested()).isFalse();
        assertThat(executionStore.get("exec-queued").getStatus())
                .isEqualTo(ExecutionStatus.CANCELED);
    }

    @Test
    void rejectsCancelOfTerminalExecution() {
        JobExecutionService service = service(List.of(handler(JobHandlerResult::ok)), null);
        execution("exec-done", ExecutionStatus.SUCCESS);

        assertThatThrownBy(() -> service.cancel("exec-done"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(409);
                    assertThat(exception.getCode()).isEqualTo("JOB_EXECUTION_NOT_CANCELABLE");
                });
    }

    @Test
    void skipsExecutionWhenPreviousRunStillHoldsTheLock() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        JobHandler blocking = new JobHandler() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public JobHandlerResult execute(JobExecutionContext context) throws Exception {
                started.countDown();
                release.await();
                return JobHandlerResult.ok("released");
            }
        };
        JobExecutionService service = service(List.of(blocking), executor);
        JobDefinitionEntity job = job("job-1", BlockStrategy.DISCARD, 0, 0, 0);

        String firstExecutionId = service.trigger(job, TriggerType.MANUAL, "tester", null);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        String secondExecutionId = service.trigger(job, TriggerType.MANUAL, "tester", null);

        awaitStatus(secondExecutionId, ExecutionStatus.SKIPPED);
        assertThat(executionStore.get(secondExecutionId).getErrorMessage())
                .contains("still running");

        release.countDown();
        awaitStatus(firstExecutionId, ExecutionStatus.SUCCESS);
    }

    @Test
    void rejectsTriggerWhenExecutorIsDisabled() {
        properties.setEnabled(false);
        JobExecutionService service = service(List.of(handler(JobHandlerResult::ok)), null);
        JobDefinitionEntity job = job("job-1", BlockStrategy.SERIAL, 0, 0, 0);

        assertThatThrownBy(() -> service.trigger(job, TriggerType.MANUAL, "tester", null))
                .isInstanceOfSatisfying(ApiException.class, apiException -> {
                    assertThat(apiException.getStatus()).isEqualTo(503);
                    assertThat(apiException.getCode()).isEqualTo("JOB_EXECUTOR_DISABLED");
                });
    }

    @Test
    void doesNotTriggerDisabledJobFromScheduler() {
        JobExecutionService service = service(List.of(handler(JobHandlerResult::ok)), null);
        JobDefinitionEntity job = job("job-1", BlockStrategy.SERIAL, 0, 0, 0);
        job.setStatus(JobStatus.DISABLED);

        service.executeScheduled("job-1");

        assertThat(executionStore).isEmpty();
    }

    @Test
    void recoversAbandonedExecutionsOfThisInstance() {
        JobExecutionService service = service(List.of(handler(JobHandlerResult::ok)), null);
        JobExecutionEntity queued = execution("exec-1", ExecutionStatus.QUEUED);
        JobExecutionEntity running = execution("exec-2", ExecutionStatus.RUNNING);
        when(executionRepository.findByStatusInAndInstanceName(anyList(),
                eq("test-instance"))).thenReturn(List.of(queued, running));

        int recovered = service.recoverAbandonedExecutions();

        assertThat(recovered).isEqualTo(2);
        assertThat(queued.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(running.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(queued.getErrorMessage()).contains("executor restarted");
        verify(executionRepository, times(2)).save(any(JobExecutionEntity.class));
    }

    private JobExecutionService service(List<JobHandler> handlers, TaskExecutor taskExecutor) {
        return new JobExecutionService(definitionRepository, executionRepository,
                new JobHandlerRegistry(handlers),
                taskExecutor == null ? new SyncTaskExecutor() : taskExecutor,
                scheduler, properties);
    }

    private JobHandler handler(HandlerBody body) {
        return new JobHandler() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public JobHandlerResult execute(JobExecutionContext context) {
                return body.run();
            }
        };
    }

    private JobDefinitionEntity job(String id, BlockStrategy blockStrategy, int maxRetries,
                                     int retryIntervalSeconds, int timeoutSeconds) {
        JobDefinitionEntity job = new JobDefinitionEntity();
        job.setId(id);
        job.setCode("code-" + id);
        job.setName("Job " + id);
        job.setHandlerName("test");
        job.setScheduleType(ScheduleType.MANUAL);
        job.setBlockStrategy(blockStrategy);
        job.setStatus(JobStatus.ENABLED);
        job.setMaxRetries(maxRetries);
        job.setRetryIntervalSeconds(retryIntervalSeconds);
        job.setTimeoutSeconds(timeoutSeconds);
        definitionStore.put(id, job);
        return job;
    }

    private JobExecutionEntity execution(String id, ExecutionStatus status) {
        JobExecutionEntity execution = new JobExecutionEntity();
        execution.setId(id);
        execution.setJobId("job-1");
        execution.setJobCode("code-job-1");
        execution.setHandlerName("test");
        execution.setTriggerType(TriggerType.MANUAL);
        execution.setStatus(status);
        execution.setAttempt(1);
        execution.setMaxAttempts(1);
        execution.setInstanceName("test-instance");
        executionStore.put(id, execution);
        return execution;
    }

    private void awaitStatus(String executionId, ExecutionStatus expected) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            JobExecutionEntity execution = executionStore.get(executionId);
            if (execution != null && execution.getStatus() == expected) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        JobExecutionEntity execution = executionStore.get(executionId);
        fail("Execution " + executionId + " did not reach " + expected + ", actual status: "
                + (execution == null ? "missing" : execution.getStatus()));
    }

    @FunctionalInterface
    private interface HandlerBody {
        JobHandlerResult run();
    }
}