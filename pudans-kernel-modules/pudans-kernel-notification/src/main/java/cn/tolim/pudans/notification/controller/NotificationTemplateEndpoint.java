package cn.tolim.pudans.notification.controller;

import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import cn.tolim.pudans.notification.dto.TemplateRequest;
import cn.tolim.pudans.notification.dto.TemplateResponse;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.service.NotificationQueryService;
import cn.tolim.pudans.notification.service.NotificationTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@Tag(name = "通知模板")
@RequestMapping(value = NotificationApiPaths.TEMPLATES, produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationTemplateEndpoint {

    private final NotificationTemplateService templateService;
    private final NotificationQueryService queryService;

    public NotificationTemplateEndpoint(NotificationTemplateService templateService,
                                        NotificationQueryService queryService) {
        this.templateService = templateService;
        this.queryService = queryService;
    }

    @Operation(summary = "创建模板")
    @PostMapping
    public ApiRestResult<TemplateResponse> create(@Valid @RequestBody TemplateRequest request) {
        return ApiRestResult.ok(templateService.create(request));
    }

    @Operation(summary = "更新模板")
    @PutMapping("/{id}")
    public ApiRestResult<TemplateResponse> update(@PathVariable String id,
                                                  @Valid @RequestBody TemplateRequest request) {
        return ApiRestResult.ok(templateService.update(id, request));
    }

    @Operation(summary = "删除模板")
    @DeleteMapping("/{id}")
    public ApiRestResult<Void> delete(@PathVariable String id) {
        templateService.delete(id);
        return ApiRestResult.ok();
    }

    @Operation(summary = "查询模板详情")
    @GetMapping("/{id}")
    public ApiRestResult<TemplateResponse> get(@PathVariable String id) {
        return ApiRestResult.ok(templateService.get(id));
    }

    @Operation(summary = "查询模板列表")
    @GetMapping
    public ApiRestResult<Page<TemplateResponse>> list(
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), boundedSize(size),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return ApiRestResult.ok(queryService.searchTemplates(channel, enabled, keyword, pageable));
    }

    private int boundedSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 200);
    }
}