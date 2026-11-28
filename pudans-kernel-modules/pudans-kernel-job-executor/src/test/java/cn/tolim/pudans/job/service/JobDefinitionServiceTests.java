package cn.tolim.pudans.job.service;

import cn.tolim.pudans.communal.core.error.ApiException;
import cn.tolim.pudans.job.configuration.JobExecutorProperties;
import cn.tolim.pudans.job.dto.JobDefinitionRequest;
import cn.tolim.pudans.job.dto.JobDefinitionResponse;
import cn.tolim.pudans.job.dto.PageResponse;
import cn.tolim.pudans.job.entity.JobDefinitionEntity;
import cn.tolim.pudans.job.enums.BlockStrategy;
import cn.tolim.pudans.job.enums.JobStatus;
import cn.tolim.pudans.job.enums.ScheduleType;
import cn.tolim.pudans.job.handler.JobHandlerRegistry;
import cn.tolim.pudans.job.repository.JobDefinitionRepository;
import cn.tolim.pudans.job.scheduling.JobScheduleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobDefinitionServiceTests {

    private JobDefinitionRepository repository;
    private JobHandlerRegistry handlerRegistry;
    private JobScheduleManager scheduleManager;
    private JobDefinitionService service;

    @BeforeEach
    void setUp() {
        repository = mock(JobDefinitionRepository.class);
        handlerRegistry = mock(JobHandlerRegistry.class);
        scheduleManager = mock(JobScheduleManager.class);
        JobExecutorProperties properties = new JobExecutorProperties();
        properties.setDefaultTimeoutSeconds(300);
        service = new JobDefinitionService(repository, handlerRegistry, scheduleManager,
                properties);
        when(handlerRegistry.contains(anyString())).thenReturn(true);
        when(repository.existsByCode(anyString())).thenReturn(false);
        when(repository.save(any(JobDefinitionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(scheduleManager.nextFireTime(any(JobDefinitionEntity.class)))
                .thenReturn(Optional.empty());
    }

    @Test
    void createsEnabledJobWithDefaults() {
        JobDefinitionResponse response = service.create(cronRequest(true), "alice");

        assertThat(response.code()).isEqualTo("nightly-report");
        assertThat(response.name()).isEqualTo("Nightly Report");
        assertThat(response.handlerName()).isEqualTo("noop");
        assertThat(response.scheduleType()).isEqualTo(ScheduleType.CRON);
        assertThat(response.status()).isEqualTo(JobStatus.ENABLED);
        assertThat(response.timeoutSeconds()).isEqualTo(300);
        assertThat(response.maxRetries()).isZero();
        assertThat(response.retryIntervalSeconds()).isZero();
        assertThat(response.blockStrategy()).isEqualTo(BlockStrategy.SERIAL);
        assertThat(response.createBy()).isEqualTo("alice");
        assertThat(response.scheduled()).isFalse();
        verify(scheduleManager).scheduleAfterCommit(any(JobDefinitionEntity.class));
    }

    @Test
    void createsDisabledJobWithoutScheduling() {
        JobDefinitionResponse response = service.create(cronRequest(false), "alice");

        assertThat(response.status()).isEqualTo(JobStatus.DISABLED);
        verify(scheduleManager, never()).scheduleAfterCommit(any(JobDefinitionEntity.class));
        verify(scheduleManager).unschedule(response.id());
    }

    @Test
    void rejectsDuplicateCode() {
        when(repository.existsByCode("nightly-report")).thenReturn(true);

        assertThatThrownBy(() -> service.create(cronRequest(true), "alice"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(409);
                    assertThat(exception.getCode()).isEqualTo("JOB_CODE_EXISTS");
                });
    }

    @Test
    void rejectsUnknownHandler() {
        when(handlerRegistry.contains("noop")).thenReturn(false);

        assertThatThrownBy(() -> service.create(cronRequest(true), "alice"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(400);
                    assertThat(exception.getCode()).isEqualTo("JOB_HANDLER_NOT_FOUND");
                });
    }

    @Test
    void rejectsInvalidCronExpression() {
        JobDefinitionRequest request = new JobDefinitionRequest("bad-cron", "Bad Cron", "noop",
                ScheduleType.CRON, "not-a-cron", null, null, null, null, null, null, null, true,
                null, null);

        assertThatThrownBy(() -> service.create(request, "alice"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(400);
                    assertThat(exception.getCode()).isEqualTo("JOB_SCHEDULE_INVALID");
                });
    }

    @Test
    void rejectsFixedRateWithoutPeriod() {
        JobDefinitionRequest request = new JobDefinitionRequest("fixed", "Fixed", "noop",
                ScheduleType.FIXED_RATE, null, null, null, null, null, null, null, null, true,
                null, null);

        assertThatThrownBy(() -> service.create(request, "alice"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(400);
                    assertThat(exception.getCode()).isEqualTo("JOB_SCHEDULE_INVALID");
                });
    }

    @Test
    void manualJobNeedsNoScheduleParameters() {
        JobDefinitionRequest request = new JobDefinitionRequest("manual-job", "Manual Job",
                "noop", ScheduleType.MANUAL, null, null, null, "{}", 60, 2, 5,
                BlockStrategy.PARALLEL, true, "manual job", "remark");

        JobDefinitionResponse response = service.create(request, "bob");

        assertThat(response.scheduleType()).isEqualTo(ScheduleType.MANUAL);
        assertThat(response.cronExpression()).isNull();
        assertThat(response.periodMillis()).isNull();
        assertThat(response.maxRetries()).isEqualTo(2);
        assertThat(response.blockStrategy()).isEqualTo(BlockStrategy.PARALLEL);
        assertThat(response.remark()).isEqualTo("remark");
        verify(scheduleManager).scheduleAfterCommit(any(JobDefinitionEntity.class));
    }

    @Test
    void updatesExistingJobAndReschedules() {
        JobDefinitionEntity entity = entity();
        when(repository.findById("job-1")).thenReturn(Optional.of(entity));
        JobDefinitionRequest request = new JobDefinitionRequest("nightly-report-v2",
                "Nightly Report V2", "noop", ScheduleType.FIXED_RATE, null, 60_000L, 1_000L,
                "{\"x\":2}", 120, 1, 10, BlockStrategy.DISCARD, true, "updated", null);

        JobDefinitionResponse response = service.update("job-1", request, "carol");

        assertThat(response.code()).isEqualTo("nightly-report-v2");
        assertThat(response.name()).isEqualTo("Nightly Report V2");
        assertThat(response.scheduleType()).isEqualTo(ScheduleType.FIXED_RATE);
        assertThat(response.cronExpression()).isNull();
        assertThat(response.periodMillis()).isEqualTo(60_000L);
        assertThat(response.timeoutSeconds()).isEqualTo(120);
        assertThat(response.updateBy()).isEqualTo("carol");
        verify(scheduleManager).scheduleAfterCommit(entity);
    }

    @Test
    void updateRejectsUnknownJob() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("missing", manualRequest(), "carol"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(404);
                    assertThat(exception.getCode()).isEqualTo("JOB_NOT_FOUND");
                });
    }

    @Test
    void updateRejectsCodeUsedByAnotherJob() {
        JobDefinitionEntity entity = entity();
        when(repository.findById("job-1")).thenReturn(Optional.of(entity));
        when(repository.existsByCode("taken")).thenReturn(true);
        JobDefinitionRequest request = new JobDefinitionRequest("taken", "Name", "noop",
                ScheduleType.MANUAL, null, null, null, null, null, null, null, null, true, null,
                null);

        assertThatThrownBy(() -> service.update("job-1", request, "carol"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("JOB_CODE_EXISTS"));
    }

    @Test
    void enablingAndDisablingUpdatesSchedule() {
        JobDefinitionEntity entity = entity();
        entity.setStatus(JobStatus.DISABLED);
        when(repository.findById("job-1")).thenReturn(Optional.of(entity));

        JobDefinitionResponse enabled = service.setEnabled("job-1", true, "dave");
        assertThat(enabled.status()).isEqualTo(JobStatus.ENABLED);
        verify(scheduleManager).scheduleAfterCommit(entity);

        JobDefinitionResponse disabled = service.setEnabled("job-1", false, "dave");
        assertThat(disabled.status()).isEqualTo(JobStatus.DISABLED);
        verify(scheduleManager).unschedule("job-1");
    }

    @Test
    void deleteUnschedulesAndRemovesDefinition() {
        JobDefinitionEntity entity = entity();
        when(repository.findById("job-1")).thenReturn(Optional.of(entity));

        service.delete("job-1");

        verify(scheduleManager).unschedule("job-1");
        verify(repository).delete(entity);
    }

    @Test
    void listsDefinitionsWithScheduleInformation() {
        JobDefinitionEntity entity = entity();
        entity.setStatus(JobStatus.ENABLED);
        Page<JobDefinitionEntity> page = new PageImpl<>(List.of(entity));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(scheduleManager.isScheduled("job-1")).thenReturn(true);
        when(scheduleManager.nextFireTime(entity))
                .thenReturn(Optional.of(Instant.parse("2030-01-01T00:00:00Z")));

        PageResponse<JobDefinitionResponse> response = service.list(JobStatus.ENABLED, "night",
                0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().scheduled()).isTrue();
        assertThat(response.content().getFirst().nextFireTime())
                .isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    private JobDefinitionRequest cronRequest(boolean enabled) {
        return new JobDefinitionRequest("nightly-report", "Nightly Report", "noop",
                ScheduleType.CRON, "0 0 2 * * *", null, null, "{}", null, null, null, null,
                enabled, "daily report", null);
    }

    private JobDefinitionRequest manualRequest() {
        return new JobDefinitionRequest("manual-job", "Manual Job", "noop", ScheduleType.MANUAL,
                null, null, null, null, null, null, null, null, true, null, null);
    }

    private JobDefinitionEntity entity() {
        JobDefinitionEntity entity = new JobDefinitionEntity();
        entity.setId("job-1");
        entity.setCode("nightly-report");
        entity.setName("Nightly Report");
        entity.setHandlerName("noop");
        entity.setScheduleType(ScheduleType.CRON);
        entity.setCronExpression("0 0 2 * * *");
        entity.setStatus(JobStatus.DISABLED);
        return entity;
    }
}