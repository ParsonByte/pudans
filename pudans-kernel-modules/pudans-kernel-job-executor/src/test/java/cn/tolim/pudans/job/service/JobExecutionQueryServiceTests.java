package cn.tolim.pudans.job.service;

import cn.tolim.pudans.communal.core.error.ApiException;
import cn.tolim.pudans.job.dto.JobExecutionQuery;
import cn.tolim.pudans.job.dto.JobExecutionResponse;
import cn.tolim.pudans.job.dto.JobExecutionStatsResponse;
import cn.tolim.pudans.job.dto.PageResponse;
import cn.tolim.pudans.job.entity.JobExecutionEntity;
import cn.tolim.pudans.job.enums.ExecutionStatus;
import cn.tolim.pudans.job.enums.TriggerType;
import cn.tolim.pudans.job.repository.JobDefinitionRepository;
import cn.tolim.pudans.job.repository.JobExecutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobExecutionQueryServiceTests {

    private JobExecutionRepository executionRepository;
    private JobDefinitionRepository definitionRepository;
    private JobExecutionQueryService service;

    @BeforeEach
    void setUp() {
        executionRepository = mock(JobExecutionRepository.class);
        definitionRepository = mock(JobDefinitionRepository.class);
        service = new JobExecutionQueryService(executionRepository, definitionRepository);
    }

    @Test
    void returnsExecutionById() {
        JobExecutionEntity execution = execution();
        when(executionRepository.findById("exec-1")).thenReturn(Optional.of(execution));

        JobExecutionResponse response = service.get("exec-1");

        assertThat(response.id()).isEqualTo("exec-1");
        assertThat(response.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(response.jobCode()).isEqualTo("job-code");
    }

    @Test
    void missingExecutionIsReportedAsNotFound() {
        when(executionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("missing"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(404);
                    assertThat(exception.getCode()).isEqualTo("JOB_EXECUTION_NOT_FOUND");
                });
    }

    @Test
    void listsExecutionsWithPagination() {
        JobExecutionEntity execution = execution();
        Page<JobExecutionEntity> page = new PageImpl<>(List.of(execution));
        when(executionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);
        JobExecutionQuery query = new JobExecutionQuery("job-1", null, ExecutionStatus.SUCCESS,
                TriggerType.MANUAL, LocalDateTime.now().minusDays(1), null, 0, 10);

        PageResponse<JobExecutionResponse> response = service.list(query);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().id()).isEqualTo("exec-1");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void aggregatesExecutionCounters() {
        when(definitionRepository.existsById("job-1")).thenReturn(true);
        JobExecutionEntity execution = execution();
        when(executionRepository.findFirstByJobIdOrderByCreateTimeDesc("job-1"))
                .thenReturn(Optional.of(execution));
        when(executionRepository.countByJobId("job-1")).thenReturn(10L);
        when(executionRepository.countByJobIdAndStatus("job-1", ExecutionStatus.QUEUED))
                .thenReturn(1L);
        when(executionRepository.countByJobIdAndStatus("job-1", ExecutionStatus.RUNNING))
                .thenReturn(2L);
        when(executionRepository.countByJobIdAndStatus("job-1", ExecutionStatus.SUCCESS))
                .thenReturn(3L);
        when(executionRepository.countByJobIdAndStatus("job-1", ExecutionStatus.FAILED))
                .thenReturn(2L);
        when(executionRepository.countByJobIdAndStatus("job-1", ExecutionStatus.TIMEOUT))
                .thenReturn(1L);
        when(executionRepository.countByJobIdAndStatus("job-1", ExecutionStatus.SKIPPED))
                .thenReturn(1L);
        when(executionRepository.countByJobIdAndStatus("job-1", ExecutionStatus.CANCELED))
                .thenReturn(0L);

        JobExecutionStatsResponse response = service.stats("job-1");

        assertThat(response.totalCount()).isEqualTo(10);
        assertThat(response.queuedCount()).isEqualTo(1);
        assertThat(response.runningCount()).isEqualTo(2);
        assertThat(response.successCount()).isEqualTo(3);
        assertThat(response.failedCount()).isEqualTo(2);
        assertThat(response.timeoutCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isEqualTo(1);
        assertThat(response.canceledCount()).isZero();
        assertThat(response.lastExecution()).isNotNull();
        assertThat(response.lastExecution().id()).isEqualTo("exec-1");
    }

    @Test
    void statsForUnknownJobAreRejected() {
        when(definitionRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> service.stats("missing"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("JOB_NOT_FOUND"));
    }

    private JobExecutionEntity execution() {
        JobExecutionEntity execution = new JobExecutionEntity();
        execution.setId("exec-1");
        execution.setJobId("job-1");
        execution.setJobCode("job-code");
        execution.setJobName("Job");
        execution.setHandlerName("noop");
        execution.setTriggerType(TriggerType.MANUAL);
        execution.setStatus(ExecutionStatus.SUCCESS);
        execution.setAttempt(1);
        execution.setMaxAttempts(1);
        execution.setStartTime(LocalDateTime.now().minusSeconds(1));
        execution.setEndTime(LocalDateTime.now());
        execution.setDurationMs(1000L);
        return execution;
    }
}