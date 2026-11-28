package cn.tolim.pudans.job.contract;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.job.configuration.JobExecutorProperties;
import cn.tolim.pudans.job.dto.ExecutorStatusResponse;
import cn.tolim.pudans.job.dto.ScheduledJobInfo;
import cn.tolim.pudans.job.enums.ExecutionStatus;
import cn.tolim.pudans.job.enums.JobStatus;
import cn.tolim.pudans.job.handler.JobHandlerRegistry;
import cn.tolim.pudans.job.repository.JobDefinitionRepository;
import cn.tolim.pudans.job.repository.JobExecutionRepository;
import cn.tolim.pudans.job.scheduling.JobScheduleManager;
import cn.tolim.pudans.job.support.JobConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

// REST API exposing executor and scheduler status
@RestController
@Tag(name = "任务执行器")
@RequestMapping(value = JobConstants.EXECUTOR_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class JobExecutorEndpoint {

    private final JobExecutorProperties properties;
    private final JobScheduleManager jobScheduleManager;
    private final JobHandlerRegistry jobHandlerRegistry;
    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final ThreadPoolTaskExecutor jobExecutionExecutor;

    public JobExecutorEndpoint(JobExecutorProperties properties,
                               JobScheduleManager jobScheduleManager,
                               JobHandlerRegistry jobHandlerRegistry,
                               JobDefinitionRepository jobDefinitionRepository,
                               JobExecutionRepository jobExecutionRepository,
                               @Qualifier("jobExecutionExecutor")
                               ThreadPoolTaskExecutor jobExecutionExecutor) {
        this.properties = properties;
        this.jobScheduleManager = jobScheduleManager;
        this.jobHandlerRegistry = jobHandlerRegistry;
        this.jobDefinitionRepository = jobDefinitionRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.jobExecutionExecutor = jobExecutionExecutor;
    }

    @Operation(summary = "查询执行器与调度器状态")
    @GetMapping("/status")
    public ApiResponse<ExecutorStatusResponse> status() {
        int queueSize = jobExecutionExecutor.getThreadPoolExecutor().getQueue().size();
        ExecutorStatusResponse response = new ExecutorStatusResponse(
                properties.getInstanceName(),
                properties.isEnabled(),
                jobScheduleManager.scheduledCount(),
                jobDefinitionRepository.count(),
                jobDefinitionRepository.countByStatus(JobStatus.ENABLED),
                jobExecutionRepository.countByStatus(ExecutionStatus.QUEUED),
                jobExecutionRepository.countByStatus(ExecutionStatus.RUNNING),
                jobExecutionExecutor.getPoolSize(),
                jobExecutionExecutor.getActiveCount(),
                queueSize,
                properties.getZone(),
                jobHandlerRegistry.names(),
                Instant.now());
        return ApiResponse.ok(response);
    }

    @Operation(summary = "查询调度器当前任务")
    @GetMapping("/scheduled")
    public ApiResponse<List<ScheduledJobInfo>> scheduled() {
        return ApiResponse.ok(jobScheduleManager.scheduledJobs());
    }
}