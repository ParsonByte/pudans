package cn.tolim.pudans.job.scheduling;

import cn.tolim.pudans.job.configuration.JobExecutorProperties;
import cn.tolim.pudans.job.dto.ScheduledJobInfo;
import cn.tolim.pudans.job.entity.JobDefinitionEntity;
import cn.tolim.pudans.job.enums.JobStatus;
import cn.tolim.pudans.job.enums.ScheduleType;
import cn.tolim.pudans.job.repository.JobDefinitionRepository;
import cn.tolim.pudans.job.service.JobExecutionService;
import cn.tolim.pudans.job.support.JobErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

// Keeps the TaskScheduler in sync with enabled job definitions
@Component
public class JobScheduleManager implements DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobScheduleManager.class);

    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobExecutionService jobExecutionService;
    private final TaskScheduler taskScheduler;
    private final JobExecutorProperties properties;

    private final ConcurrentMap<String, ScheduledJobHandle> handles = new ConcurrentHashMap<>();

    public JobScheduleManager(JobDefinitionRepository jobDefinitionRepository,
                              JobExecutionService jobExecutionService,
                              @Qualifier("jobTaskScheduler") TaskScheduler taskScheduler,
                              JobExecutorProperties properties) {
        this.jobDefinitionRepository = jobDefinitionRepository;
        this.jobExecutionService = jobExecutionService;
        this.taskScheduler = taskScheduler;
        this.properties = properties;
    }

    // Recover abandoned executions and schedule enabled jobs once the application is ready
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!properties.isEnabled() || !properties.isAutoStart()) {
            return;
        }
        try {
            int recovered = jobExecutionService.recoverAbandonedExecutions();
            refresh();
            LOGGER.info("Job executor '{}' started with {} scheduled job(s), {} stale execution(s) recovered",
                    properties.getInstanceName(), handles.size(), recovered);
        } catch (RuntimeException ex) {
            LOGGER.error("Failed to start the job executor", ex);
        }
    }

    // Reload all enabled definitions and rebuild the schedule
    public synchronized void refresh() {
        for (String jobId : List.copyOf(handles.keySet())) {
            unschedule(jobId);
        }
        jobDefinitionRepository.findAllByStatus(JobStatus.ENABLED).forEach(this::scheduleNow);
    }

    // Replace the schedule of one definition
    public synchronized void schedule(JobDefinitionEntity job) {
        if (job == null || job.getId() == null) {
            return;
        }
        unschedule(job.getId());
        scheduleNow(job);
    }

    // Schedule after the surrounding transaction commits, otherwise immediately
    public void scheduleAfterCommit(JobDefinitionEntity job) {
        if (job == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            schedule(job);
                        }
                    });
        } else {
            schedule(job);
        }
    }

    // Remove a job from the scheduler
    public synchronized void unschedule(String jobId) {
        if (jobId == null) {
            return;
        }
        ScheduledJobHandle handle = handles.remove(jobId);
        if (handle != null && handle.future() != null) {
            handle.future().cancel(false);
        }
    }

    // Whether the job currently has a scheduler handle
    public boolean isScheduled(String jobId) {
        return jobId != null && handles.containsKey(jobId);
    }

    // Number of jobs currently held by the scheduler
    public int scheduledCount() {
        return handles.size();
    }

    // Snapshot of the current schedule
    public List<ScheduledJobInfo> scheduledJobs() {
        return handles.values().stream()
                .map(handle -> new ScheduledJobInfo(handle.jobId(), handle.jobCode(),
                        handle.scheduleType(), nextFireTime(handle)))
                .sorted(Comparator.comparing(ScheduledJobInfo::jobCode,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    // Computed next fire time for a definition, if it is scheduled
    public Optional<Instant> nextFireTime(JobDefinitionEntity job) {
        if (job == null || job.getId() == null || job.getStatus() != JobStatus.ENABLED) {
            return Optional.empty();
        }
        ScheduledJobHandle handle = handles.get(job.getId());
        if (handle == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(nextFireTime(handle));
    }

    @Override
    public void destroy() {
        for (ScheduledJobHandle handle : handles.values()) {
            if (handle.future() != null) {
                handle.future().cancel(true);
            }
        }
        handles.clear();
    }

    private void scheduleNow(JobDefinitionEntity job) {
        if (job.getStatus() != JobStatus.ENABLED || job.getScheduleType() == null) {
            return;
        }
        String jobId = job.getId();
        try {
            ScheduledJobHandle handle = switch (job.getScheduleType()) {
                case CRON -> scheduleCron(job);
                case FIXED_RATE -> scheduleFixed(job, true);
                case FIXED_DELAY -> scheduleFixed(job, false);
                case MANUAL -> null;
            };
            if (handle != null) {
                handles.put(jobId, handle);
            }
        } catch (RuntimeException ex) {
            LOGGER.error("Failed to schedule job {} ({})", job.getCode(), jobId, ex);
            throw ex;
        }
    }

    private ScheduledJobHandle scheduleCron(JobDefinitionEntity job) {
        CronExpression expression = parseCron(job.getCronExpression());
        Runnable task = () -> jobExecutionService.executeScheduled(job.getId());
        CronTrigger trigger = new CronTrigger(job.getCronExpression(), properties.zoneId());
        ScheduledFuture<?> future = taskScheduler.schedule(task, trigger);
        if (future == null) {
            throw JobErrors.invalidSchedule("Cron trigger was not accepted: "
                    + job.getCronExpression());
        }
        return new ScheduledJobHandle(job.getId(), job.getCode(), ScheduleType.CRON, future,
                expression, new AtomicReference<>());
    }

    private ScheduledJobHandle scheduleFixed(JobDefinitionEntity job, boolean fixedRate) {
        long periodMillis = requirePeriod(job);
        long initialDelayMillis = job.getInitialDelayMillis() == null
                ? 0 : Math.max(0, job.getInitialDelayMillis());
        Instant start = Instant.now().plusMillis(initialDelayMillis);
        AtomicReference<Instant> nextFireTime = new AtomicReference<>(start);
        Runnable task = () -> {
            nextFireTime.set(Instant.now().plusMillis(periodMillis));
            jobExecutionService.executeScheduled(job.getId());
        };
        ScheduledFuture<?> future = fixedRate
                ? taskScheduler.scheduleAtFixedRate(task, start, Duration.ofMillis(periodMillis))
                : taskScheduler.scheduleWithFixedDelay(task, start, Duration.ofMillis(periodMillis));
        if (future == null) {
            throw JobErrors.invalidSchedule("Fixed schedule was not accepted for job "
                    + job.getCode());
        }
        return new ScheduledJobHandle(job.getId(), job.getCode(), job.getScheduleType(), future,
                null, nextFireTime);
    }

    private Instant nextFireTime(ScheduledJobHandle handle) {
        if (handle.scheduleType() == ScheduleType.CRON && handle.cronExpression() != null) {
            ZonedDateTime next = handle.cronExpression().next(ZonedDateTime.now(properties.zoneId()));
            return next == null ? null : next.toInstant();
        }
        AtomicReference<Instant> reference = handle.nextFireTime();
        return reference == null ? null : reference.get();
    }

    private CronExpression parseCron(String expression) {
        if (expression == null || expression.isBlank()) {
            throw JobErrors.invalidSchedule("Cron expression is required for CRON schedules");
        }
        try {
            return CronExpression.parse(expression);
        } catch (IllegalArgumentException ex) {
            throw JobErrors.invalidSchedule("Invalid cron expression '" + expression + "': "
                    + ex.getMessage());
        }
    }

    private long requirePeriod(JobDefinitionEntity job) {
        Long period = job.getPeriodMillis();
        if (period == null || period <= 0) {
            throw JobErrors.invalidSchedule("periodMillis must be positive for "
                    + job.getScheduleType() + " schedules");
        }
        return period;
    }
}