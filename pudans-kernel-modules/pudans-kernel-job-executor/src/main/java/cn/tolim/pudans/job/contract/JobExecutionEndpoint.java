package cn.tolim.pudans.job.contract;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.job.dto.JobExecutionQuery;
import cn.tolim.pudans.job.dto.JobExecutionResponse;
import cn.tolim.pudans.job.dto.JobExecutionStatsResponse;
import cn.tolim.pudans.job.dto.PageResponse;
import cn.tolim.pudans.job.enums.ExecutionStatus;
import cn.tolim.pudans.job.enums.TriggerType;
import cn.tolim.pudans.job.service.JobExecutionQueryService;
import cn.tolim.pudans.job.service.JobExecutionService;
import cn.tolim.pudans.job.support.JobConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

// REST API for execution history, statistics and cancellation
@Validated
@RestController
@Tag(name = "任务执行")
@RequestMapping(value = JobConstants.EXECUTIONS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class JobExecutionEndpoint {

    private final JobExecutionService jobExecutionService;
    private final JobExecutionQueryService jobExecutionQueryService;

    public JobExecutionEndpoint(JobExecutionService jobExecutionService,
                                JobExecutionQueryService jobExecutionQueryService) {
        this.jobExecutionService = jobExecutionService;
        this.jobExecutionQueryService = jobExecutionQueryService;
    }

    @Operation(summary = "查询任务执行列表")
    @GetMapping
    public ApiResponse<PageResponse<JobExecutionResponse>> list(
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) String jobCode,
            @RequestParam(required = false) ExecutionStatus status,
            @RequestParam(required = false) TriggerType triggerType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        JobExecutionQuery query = new JobExecutionQuery(jobId, jobCode, status, triggerType,
                from, to, page, size);
        return ApiResponse.ok(jobExecutionQueryService.list(query));
    }

    @Operation(summary = "查询任务执行详情")
    @GetMapping("/{id}")
    public ApiResponse<JobExecutionResponse> get(@PathVariable String id) {
        return ApiResponse.ok(jobExecutionQueryService.get(id));
    }

    @Operation(summary = "取消任务执行")
    @PostMapping("/{id}/cancel")
    public ApiResponse<JobExecutionResponse> cancel(@PathVariable String id) {
        return ApiResponse.ok(jobExecutionService.cancel(id));
    }

    @Operation(summary = "查询任务执行统计")
    @GetMapping("/stats/{jobId}")
    public ApiResponse<JobExecutionStatsResponse> stats(@PathVariable String jobId) {
        return ApiResponse.ok(jobExecutionQueryService.stats(jobId));
    }
}