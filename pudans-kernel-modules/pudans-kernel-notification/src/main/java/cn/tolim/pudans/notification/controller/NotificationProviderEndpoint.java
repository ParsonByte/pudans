package cn.tolim.pudans.notification.controller;

import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import cn.tolim.pudans.notification.dto.ProviderRequest;
import cn.tolim.pudans.notification.dto.ProviderResponse;
import cn.tolim.pudans.notification.dto.ProviderTestRequest;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.sender.SendResult;
import cn.tolim.pudans.notification.service.NotificationProviderService;
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
@Tag(name = "通知渠道")
@RequestMapping(value = NotificationApiPaths.PROVIDERS, produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationProviderEndpoint {

    private final NotificationProviderService providerService;

    public NotificationProviderEndpoint(NotificationProviderService providerService) {
        this.providerService = providerService;
    }

    @Operation(summary = "创建渠道")
    @PostMapping
    public ApiRestResult<ProviderResponse> create(@Valid @RequestBody ProviderRequest request) {
        return ApiRestResult.ok(providerService.create(request));
    }

    @Operation(summary = "更新渠道")
    @PutMapping("/{id}")
    public ApiRestResult<ProviderResponse> update(@PathVariable String id,
                                                  @Valid @RequestBody ProviderRequest request) {
        return ApiRestResult.ok(providerService.update(id, request));
    }

    @Operation(summary = "删除渠道")
    @DeleteMapping("/{id}")
    public ApiRestResult<Void> delete(@PathVariable String id) {
        providerService.delete(id);
        return ApiRestResult.ok();
    }

    @Operation(summary = "查询渠道详情")
    @GetMapping("/{id}")
    public ApiRestResult<ProviderResponse> get(@PathVariable String id) {
        return ApiRestResult.ok(providerService.get(id));
    }

    @Operation(summary = "查询渠道列表")
    @GetMapping
    public ApiRestResult<Page<ProviderResponse>> list(
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), boundedSize(size),
                Sort.by(Sort.Direction.ASC, "priority"));
        return ApiRestResult.ok(providerService.list(channel, enabled, keyword, pageable));
    }

    @Operation(summary = "测试渠道")
    @PostMapping("/{id}/test")
    public ApiRestResult<SendResult> test(@PathVariable String id,
                                          @Valid @RequestBody ProviderTestRequest request) {
        return ApiRestResult.ok(providerService.test(id, request));
    }

    private int boundedSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 200);
    }
}