package cn.tolim.pudans.iam.authorization.endpoint;

import cn.tolim.pudans.iam.authorization.constant.AuthorizationConstants;
import cn.tolim.pudans.iam.authorization.dto.ServiceActionRequest;
import cn.tolim.pudans.iam.authorization.dto.ServiceActionView;
import cn.tolim.pudans.iam.authorization.service.ServiceActionService;
import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.util.List;


@Validated
@Tag(name = "统一授权-动作声明")
@RestController
@RequestMapping(value = AuthorizationConstants.AUTHZ_API_PATH + "/actions",
        produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class ServiceActionEndpoint {

    private final ServiceActionService serviceActionService;

    // Lists actions declared by an application
    // Parameter appCode: String application code
    // Returns: ApiRestResult<List<ServiceActionView>>
    @Operation(summary = "查询动作列表")
    @GetMapping
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<List<ServiceActionView>> list(@RequestParam String appCode) {
        return ApiRestResult.ok(serviceActionService.listByApp(appCode));
    }

    // Returns an action declaration
    // Parameter id: String action ID
    // Returns: ApiRestResult<ServiceActionView>
    @Operation(summary = "查询动作详情")
    @GetMapping(value = "/{id}")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<ServiceActionView> get(@PathVariable String id) {
        return ApiRestResult.ok(serviceActionService.get(id));
    }

    // Registers an action declaration
    // Parameter request: ServiceActionRequest
    // Returns: ApiRestResult<ServiceActionView>
    @Operation(summary = "注册动作")
    @PostMapping
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<ServiceActionView> register(
            @Valid @RequestBody ServiceActionRequest request) {
        return ApiRestResult.ok(serviceActionService.register(request));
    }

    // Updates an action declaration
    // Parameter id: String action ID
    // Parameter request: ServiceActionRequest
    // Returns: ApiRestResult<ServiceActionView>
    @Operation(summary = "更新动作")
    @PutMapping(value = "/{id}")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<ServiceActionView> update(@PathVariable String id,
                                                   @Valid @RequestBody ServiceActionRequest request) {
        return ApiRestResult.ok(serviceActionService.update(id, request));
    }

    // Deletes an action declaration
    // Parameter id: String action ID
    // Returns: ApiRestResult<Void>
    @Operation(summary = "删除动作")
    @DeleteMapping(value = "/{id}")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<Void> delete(@PathVariable String id) {
        serviceActionService.delete(id);
        return ApiRestResult.ok();
    }
}