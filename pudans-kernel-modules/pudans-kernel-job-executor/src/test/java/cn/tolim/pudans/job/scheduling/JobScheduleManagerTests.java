package cn.tolim.pudans.job.scheduling;

import cn.tolim.pudans.communal.core.error.ApiException;
import cn.tolim.pudans.job.configuration.JobExecutorProperties;
import cn.tolim.pudans.job.entity.JobDefinitionEntity;
import cn.tolim.pudans.job.enums.JobStatus;
import cn.tolim.pudans.job.enums.ScheduleType;
import cn.tolim.pudans.job.repository.JobDefinitionRepository;
import cn.tolim.pudans.job.service.JobExecutionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JobScheduleManagerTests {

    private JobDefinitionRepository repository;
    private JobExecutionService executionService;
    private JobExecutorProperties properties;
    private ThreadPoolTaskScheduler scheduler;
    private JobScheduleManager manager;

    @BeforeEach
    void setUp() {
        repository = mock(JobDefinitionRepository.class);
        executionService = mock(JobExecutionService.class);
        properties = new JobExecutorProperties();
        properties.setInstanceName("test-instance");
        properties.setZone("UTC");
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.initialize();
        manager = new JobScheduleManager(repository, executionService, scheduler, properties);
    }

    @AfterEach
    void tearDown() {
        manager.destroy();
        scheduler.shutdown();
    }

    @Test
    void refreshSchedulesAllEnabledJobs() {
        JobDefinitionEntity cron = cronJob("job-1", "0 0 0 * * *");
        JobDefinitionEntity fixed = fixedRateJob("job-2", 60_000);
        JobDefinitionEntity disabled = cronJob("job-3", "0 0 1 * * *");
        disabled.setStatus(JobStatus.DISABLED);
        JobDefinitionEntity manual = cronJob("job-4", null);
        manual.setScheduleType(ScheduleType.MANUAL);
        when(repository.findAllByStatus(JobStatus.ENABLED))
                .thenReturn(List.of(cron, fixed, disabled, manual));

        manager.refresh();

        assertThat(manager.scheduledCount()).isEqualTo(2);
        assertThat(manager.isScheduled("job-1")).isTrue();
        assertThat(manager.isScheduled("job-2")).isTrue();
        assertThat(manager.isScheduled("job-3")).isFalse();
        assertThat(manager.isScheduled("job-4")).isFalse();
        assertThat(manager.nextFireTime(cron)).isPresent();
        assertThat(manager.scheduledJobs()).hasSize(2);
    }

    @Test
    void refreshReplacesPreviousSchedule() {
        JobDefinitionEntity first = cronJob("job-1", "0 0 0 * * *");
        JobDefinitionEntity second = cronJob("job-2", "0 0 12 * * *");
        when(repository.findAllByStatus(JobStatus.ENABLED))
                .thenReturn(List.of(first), List.of(second));

        manager.refresh();
        assertThat(manager.isScheduled("job-1")).isTrue();

        manager.refresh();
        assertThat(manager.isScheduled("job-1")).isFalse();
        assertThat(manager.isScheduled("job-2")).isTrue();
        assertThat(manager.scheduledCount()).isEqualTo(1);
    }

    @Test
    void reschedulingSameJobKeepsSingleHandle() {
        JobDefinitionEntity job = cronJob("job-1", "0 0 0 * * *");
        manager.schedule(job);
        Instant firstNextFire = manager.nextFireTime(job).orElseThrow();

        job.setCronExpression("0 0 12 * * *");
        manager.schedule(job);

        assertThat(manager.scheduledCount()).isEqualTo(1);
        assertThat(manager.nextFireTime(job)).isPresent();
        assertThat(firstNextFire).isNotNull();
    }

    @Test
    void disabledJobIsNotScheduled() {
        JobDefinitionEntity job = cronJob("job-1", "0 0 0 * * *");
        job.setStatus(JobStatus.DISABLED);

        manager.schedule(job);

        assertThat(manager.isScheduled("job-1")).isFalse();
        assertThat(manager.scheduledCount()).isZero();
    }

    @Test
    void manualJobIsNotScheduled() {
        JobDefinitionEntity job = cronJob("job-1", null);
        job.setScheduleType(ScheduleType.MANUAL);

        manager.schedule(job);

        assertThat(manager.isScheduled("job-1")).isFalse();
    }

    @Test
    void invalidCronIsRejected() {
        JobDefinitionEntity job = cronJob("job-1", "not-a-cron");

        assertThatThrownBy(() -> manager.schedule(job))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(400);
                    assertThat(exception.getCode()).isEqualTo("JOB_SCHEDULE_INVALID");
                });
        assertThat(manager.isScheduled("job-1")).isFalse();
    }

    @Test
    void fixedRateJobGetsNextFireTime() {
        JobDefinitionEntity job = fixedRateJob("job-2", 60_000);

        manager.schedule(job);

        Instant next = manager.nextFireTime(job).orElseThrow();
        Instant now = Instant.now();
        assertThat(next).isAfter(now.minusSeconds(2)).isBefore(now.plusSeconds(61));
    }

    @Test
    void cronTriggerRunsTheExecutionService() {
        TaskScheduler mockedScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(scheduled)
                .when(mockedScheduler).schedule(any(Runnable.class), any(Trigger.class));
        JobScheduleManager mockedManager = new JobScheduleManager(repository, executionService,
                mockedScheduler, properties);
        JobDefinitionEntity job = cronJob("job-1", "0 0 0 * * *");

        mockedManager.schedule(job);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(mockedScheduler).schedule(task.capture(), any(Trigger.class));
        task.getValue().run();

        verify(executionService).executeScheduled("job-1");
    }

    @Test
    void destroyCancelsEverySchedule() {
        manager.schedule(cronJob("job-1", "0 0 0 * * *"));
        manager.schedule(fixedRateJob("job-2", 30_000));
        assertThat(manager.scheduledCount()).isEqualTo(2);

        manager.destroy();

        assertThat(manager.scheduledCount()).isZero();
    }

    @Test
    void startupDoesNothingWhenAutoStartIsDisabled() {
        properties.setAutoStart(false);

        manager.onApplicationReady();

        assertThat(manager.scheduledCount()).isZero();
        verifyNoInteractions(repository, executionService);
    }

    @Test
    void startupRecoversExecutionsAndSchedulesJobs() {
        JobDefinitionEntity job = cronJob("job-1", "0 0 0 * * *");
        when(executionService.recoverAbandonedExecutions()).thenReturn(0);
        when(repository.findAllByStatus(JobStatus.ENABLED)).thenReturn(List.of(job));

        manager.onApplicationReady();

        verify(executionService).recoverAbandonedExecutions();
        assertThat(manager.isScheduled("job-1")).isTrue();
    }

    private JobDefinitionEntity cronJob(String id, String cronExpression) {
        JobDefinitionEntity job = baseJob(id);
        job.setScheduleType(ScheduleType.CRON);
        job.setCronExpression(cronExpression);
        return job;
    }

    private JobDefinitionEntity fixedRateJob(String id, long periodMillis) {
        JobDefinitionEntity job = baseJob(id);
        job.setScheduleType(ScheduleType.FIXED_RATE);
        job.setPeriodMillis(periodMillis);
        job.setInitialDelayMillis(0L);
        return job;
    }

    private JobDefinitionEntity baseJob(String id) {
        JobDefinitionEntity job = new JobDefinitionEntity();
        job.setId(id);
        job.setCode("code-" + id);
        job.setName("Job " + id);
        job.setHandlerName("noop");
        job.setStatus(JobStatus.ENABLED);
        return job;
    }
}