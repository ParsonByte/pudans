package cn.tolim.pudans.iam.authorization.endpoint;

import cn.tolim.pudans.iam.authorization.constant.AuthorizationConstants;
import cn.tolim.pudans.iam.authorization.dto.ResourceTypeRequest;
import cn.tolim.pudans.iam.authorization.dto.ResourceTypeView;
import cn.tolim.pudans.iam.authorization.service.ResourceTypeService;
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
@Tag(name = "统一授权-资源类型声明")
@RestController
@RequestMapping(value = AuthorizationConstants.AUTHZ_API_PATH + "/resource-types",
        produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class ResourceTypeEndpoint {

    private final ResourceTypeService resourceTypeService;

    // Lists resource types declared by an application
    // Parameter appCode: String application code
    // Returns: ApiRestResult<List<ResourceTypeView>>
    @Operation(summary = "查询资源类型列表")
    @GetMapping
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<List<ResourceTypeView>> list(@RequestParam String appCode) {
        return ApiRestResult.ok(resourceTypeService.listByApp(appCode));
    }

    // Returns a resource type declaration
    // Parameter id: String resource type ID
    // Returns: ApiRestResult<ResourceTypeView>
    @Operation(summary = "查询资源类型详情")
    @GetMapping(value = "/{id}")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<ResourceTypeView> get(@PathVariable String id) {
        return ApiRestResult.ok(resourceTypeService.get(id));
    }

    // Registers a resource type declaration
    // Parameter request: ResourceTypeRequest
    // Returns: ApiRestResult<ResourceTypeView>
    @Operation(summary = "注册资源类型")
    @PostMapping
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<ResourceTypeView> register(
            @Valid @RequestBody ResourceTypeRequest request) {
        return ApiRestResult.ok(resourceTypeService.register(request));
    }

    // Updates a resource type declaration
    // Parameter id: String resource type ID
    // Parameter request: ResourceTypeRequest
    // Returns: ApiRestResult<ResourceTypeView>
    @Operation(summary = "更新资源类型")
    @PutMapping(value = "/{id}")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<ResourceTypeView> update(@PathVariable String id,
                                                  @Valid @RequestBody ResourceTypeRequest request) {
        return ApiRestResult.ok(resourceTypeService.update(id, request));
    }

    // Deletes a resource type declaration
    // Parameter id: String resource type ID
    // Returns: ApiRestResult<Void>
    @Operation(summary = "删除资源类型")
    @DeleteMapping(value = "/{id}")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<Void> delete(@PathVariable String id) {
        resourceTypeService.delete(id);
        return ApiRestResult.ok();
    }
}