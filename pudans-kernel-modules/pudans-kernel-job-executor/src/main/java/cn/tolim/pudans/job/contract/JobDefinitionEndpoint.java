package cn.tolim.pudans.job.contract;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.job.dto.JobDefinitionRequest;
import cn.tolim.pudans.job.dto.JobDefinitionResponse;
import cn.tolim.pudans.job.dto.JobExecutionResponse;
import cn.tolim.pudans.job.dto.JobHandlerDescriptor;
import cn.tolim.pudans.job.dto.JobTriggerRequest;
import cn.tolim.pudans.job.dto.PageResponse;
import cn.tolim.pudans.job.entity.JobDefinitionEntity;
import cn.tolim.pudans.job.enums.JobStatus;
import cn.tolim.pudans.job.enums.TriggerType;
import cn.tolim.pudans.job.handler.JobHandlerRegistry;
import cn.tolim.pudans.job.service.JobDefinitionService;
import cn.tolim.pudans.job.service.JobExecutionQueryService;
import cn.tolim.pudans.job.service.JobExecutionService;
import cn.tolim.pudans.job.support.JobConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// REST API for managing job definitions
@Validated
@RestController
@Tag(name = "任务定义")
@RequestMapping(value = JobConstants.JOBS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class JobDefinitionEndpoint {

    private final JobDefinitionService jobDefinitionService;
    private final JobExecutionService jobExecutionService;
    private final JobExecutionQueryService jobExecutionQueryService;
    private final JobHandlerRegistry jobHandlerRegistry;

    public JobDefinitionEndpoint(JobDefinitionService jobDefinitionService,
                                 JobExecutionService jobExecutionService,
                                 JobExecutionQueryService jobExecutionQueryService,
                                 JobHandlerRegistry jobHandlerRegistry) {
        this.jobDefinitionService = jobDefinitionService;
        this.jobExecutionService = jobExecutionService;
        this.jobExecutionQueryService = jobExecutionQueryService;
        this.jobHandlerRegistry = jobHandlerRegistry;
    }

    @Operation(summary = "创建任务定义")
    @PostMapping
    public ApiResponse<JobDefinitionResponse> create(
            @Valid @RequestBody JobDefinitionRequest request,
            @RequestHeader(value = JobConstants.ACTOR_HEADER, required = false) String actor) {
        return ApiResponse.ok(jobDefinitionService.create(request, actor));
    }

    @Operation(summary = "更新任务定义")
    @PutMapping("/{id}")
    public ApiResponse<JobDefinitionResponse> update(
            @PathVariable String id,
            @Valid @RequestBody JobDefinitionRequest request,
            @RequestHeader(value = JobConstants.ACTOR_HEADER, required = false) String actor) {
        return ApiResponse.ok(jobDefinitionService.update(id, request, actor));
    }

    @Operation(summary = "查询任务定义详情")
    @GetMapping("/{id}")
    public ApiResponse<JobDefinitionResponse> get(@PathVariable String id) {
        return ApiResponse.ok(jobDefinitionService.get(id));
    }

    @Operation(summary = "查询任务定义列表")
    @GetMapping
    public ApiResponse<PageResponse<JobDefinitionResponse>> list(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return ApiResponse.ok(jobDefinitionService.list(status, keyword, page, size));
    }

    @Operation(summary = "删除任务定义")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        jobDefinitionService.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "启用任务定义")
    @PostMapping("/{id}/enable")
    public ApiResponse<JobDefinitionResponse> enable(
            @PathVariable String id,
            @RequestHeader(value = JobConstants.ACTOR_HEADER, required = false) String actor) {
        return ApiResponse.ok(jobDefinitionService.setEnabled(id, true, actor));
    }

    @Operation(summary = "停用任务定义")
    @PostMapping("/{id}/disable")
    public ApiResponse<JobDefinitionResponse> disable(
            @PathVariable String id,
            @RequestHeader(value = JobConstants.ACTOR_HEADER, required = false) String actor) {
        return ApiResponse.ok(jobDefinitionService.setEnabled(id, false, actor));
    }

    @Operation(summary = "手动触发任务定义")
    @PostMapping("/{id}/trigger")
    public ApiResponse<JobExecutionResponse> trigger(
            @PathVariable String id,
            @Valid @RequestBody(required = false) JobTriggerRequest request,
            @RequestHeader(value = JobConstants.ACTOR_HEADER, required = false) String actor) {
        JobDefinitionEntity job = jobDefinitionService.requireEntity(id);
        String triggeredBy = request != null && request.triggeredBy() != null
                && !request.triggeredBy().isBlank()
                ? request.triggeredBy().trim()
                : (actor == null || actor.isBlank() ? JobConstants.SYSTEM_ACTOR : actor);
        String params = request == null ? null : request.params();
        String executionId = jobExecutionService.trigger(job, TriggerType.MANUAL, triggeredBy,
                params);
        return ApiResponse.ok(jobExecutionQueryService.get(executionId));
    }

    @Operation(summary = "查询已注册任务处理器")
    @GetMapping("/handlers")
    public ApiResponse<List<JobHandlerDescriptor>> handlers() {
        return ApiResponse.ok(jobHandlerRegistry.descriptors());
    }
}