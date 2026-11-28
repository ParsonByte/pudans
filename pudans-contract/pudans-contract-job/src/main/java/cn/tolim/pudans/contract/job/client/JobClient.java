package cn.tolim.pudans.contract.job.client;

import cn.tolim.pudans.contract.job.JobApiPaths;
import cn.tolim.pudans.contract.job.JobContractConstants;
import cn.tolim.pudans.contract.job.dto.ExecutorStatusResponse;
import cn.tolim.pudans.contract.job.dto.JobApiResult;
import cn.tolim.pudans.contract.job.dto.JobDefinitionRequest;
import cn.tolim.pudans.contract.job.dto.JobDefinitionResponse;
import cn.tolim.pudans.contract.job.dto.JobExecutionResponse;
import cn.tolim.pudans.contract.job.dto.JobExecutionStatsResponse;
import cn.tolim.pudans.contract.job.dto.JobHandlerDescriptor;
import cn.tolim.pudans.contract.job.dto.JobTriggerRequest;
import cn.tolim.pudans.contract.job.dto.PageResponse;
import cn.tolim.pudans.contract.job.dto.ScheduledJobInfo;
import cn.tolim.pudans.contract.job.enums.ExecutionStatus;
import cn.tolim.pudans.contract.job.enums.JobStatus;
import cn.tolim.pudans.contract.job.enums.TriggerType;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

// Job executor service contract
@FeignClient(name = JobContractConstants.SERVICE_NAME, contextId = "jobClient",
        path = JobApiPaths.BASE)
public interface JobClient {

    // Create a job definition
    // Parameter request: JobDefinitionRequest
    // Parameter actor: String
    // Returns: JobApiResult<JobDefinitionResponse>
    @PostMapping(JobApiPaths.JOBS_SUFFIX)
    JobApiResult<JobDefinitionResponse> createJob(@RequestBody JobDefinitionRequest request,
            @RequestHeader(value = JobContractConstants.ACTOR_HEADER, required = false)
            String actor);

    // Replace a job definition
    // Parameter id: String
    // Parameter request: JobDefinitionRequest
    // Parameter actor: String
    // Returns: JobApiResult<JobDefinitionResponse>
    @PutMapping(JobApiPaths.JOB_DETAIL_SUFFIX)
    JobApiResult<JobDefinitionResponse> updateJob(@PathVariable("id") String id,
            @RequestBody JobDefinitionRequest request,
            @RequestHeader(value = JobContractConstants.ACTOR_HEADER, required = false)
            String actor);

    // Load one job definition
    // Parameter id: String
    // Returns: JobApiResult<JobDefinitionResponse>
    @GetMapping(JobApiPaths.JOB_DETAIL_SUFFIX)
    JobApiResult<JobDefinitionResponse> getJob(@PathVariable("id") String id);

    // List job definitions with paging
    // Returns: JobApiResult<PageResponse<JobDefinitionResponse>>
    @GetMapping(JobApiPaths.JOBS_SUFFIX)
    JobApiResult<PageResponse<JobDefinitionResponse>> listJobs(
            @RequestParam(value = "status", required = false) JobStatus status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = JobContractConstants.DEFAULT_PAGE_NUMBER)
            int page,
            @RequestParam(value = "size", defaultValue = JobContractConstants.DEFAULT_PAGE_SIZE)
            int size);

    // Delete one job definition
    // Parameter id: String
    // Returns: JobApiResult<Void>
    @DeleteMapping(JobApiPaths.JOB_DETAIL_SUFFIX)
    JobApiResult<Void> deleteJob(@PathVariable("id") String id);

    // Enable one job definition
    // Parameter id: String
    // Parameter actor: String
    // Returns: JobApiResult<JobDefinitionResponse>
    @PostMapping(JobApiPaths.JOB_ENABLE_SUFFIX)
    JobApiResult<JobDefinitionResponse> enableJob(@PathVariable("id") String id,
            @RequestHeader(value = JobContractConstants.ACTOR_HEADER, required = false)
            String actor);

    // Disable one job definition
    // Parameter id: String
    // Parameter actor: String
    // Returns: JobApiResult<JobDefinitionResponse>
    @PostMapping(JobApiPaths.JOB_DISABLE_SUFFIX)
    JobApiResult<JobDefinitionResponse> disableJob(@PathVariable("id") String id,
            @RequestHeader(value = JobContractConstants.ACTOR_HEADER, required = false)
            String actor);

    // Trigger a job definition manually
    // Parameter id: String
    // Parameter request: JobTriggerRequest
    // Parameter actor: String
    // Returns: JobApiResult<JobExecutionResponse>
    @PostMapping(JobApiPaths.JOB_TRIGGER_SUFFIX)
    JobApiResult<JobExecutionResponse> triggerJob(@PathVariable("id") String id,
            @RequestBody(required = false) JobTriggerRequest request,
            @RequestHeader(value = JobContractConstants.ACTOR_HEADER, required = false)
            String actor);

    // List registered job handlers
    // Returns: JobApiResult<List<JobHandlerDescriptor>>
    @GetMapping(JobApiPaths.JOB_HANDLERS_SUFFIX)
    JobApiResult<List<JobHandlerDescriptor>> handlers();

    // List job executions with paging
    // Returns: JobApiResult<PageResponse<JobExecutionResponse>>
    @GetMapping(JobApiPaths.EXECUTIONS_SUFFIX)
    JobApiResult<PageResponse<JobExecutionResponse>> listExecutions(
            @RequestParam(value = "jobId", required = false) String jobId,
            @RequestParam(value = "jobCode", required = false) String jobCode,
            @RequestParam(value = "status", required = false) ExecutionStatus status,
            @RequestParam(value = "triggerType", required = false) TriggerType triggerType,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(value = "page", defaultValue = JobContractConstants.DEFAULT_PAGE_NUMBER)
            int page,
            @RequestParam(value = "size", defaultValue = JobContractConstants.DEFAULT_PAGE_SIZE)
            int size);

    // Load one job execution
    // Parameter id: String
    // Returns: JobApiResult<JobExecutionResponse>
    @GetMapping(JobApiPaths.EXECUTION_DETAIL_SUFFIX)
    JobApiResult<JobExecutionResponse> getExecution(@PathVariable("id") String id);

    // Cancel a queued or running job execution
    // Parameter id: String
    // Returns: JobApiResult<JobExecutionResponse>
    @PostMapping(JobApiPaths.EXECUTION_CANCEL_SUFFIX)
    JobApiResult<JobExecutionResponse> cancelExecution(@PathVariable("id") String id);

    // Execution counters for one job
    // Parameter jobId: String
    // Returns: JobApiResult<JobExecutionStatsResponse>
    @GetMapping(JobApiPaths.EXECUTION_STATS_SUFFIX)
    JobApiResult<JobExecutionStatsResponse> executionStats(@PathVariable("jobId") String jobId);

    // Executor and scheduler status
    // Returns: JobApiResult<ExecutorStatusResponse>
    @GetMapping(JobApiPaths.EXECUTOR_STATUS_SUFFIX)
    JobApiResult<ExecutorStatusResponse> executorStatus();

    // Jobs currently held by the scheduler
    // Returns: JobApiResult<List<ScheduledJobInfo>>
    @GetMapping(JobApiPaths.EXECUTOR_SCHEDULED_SUFFIX)
    JobApiResult<List<ScheduledJobInfo>> scheduledJobs();
}