package cn.tolim.pudans.iam.authorization.endpoint;

import cn.tolim.pudans.iam.authorization.constant.AuthorizationConstants;
import cn.tolim.pudans.iam.authorization.dto.RoleBindingView;
import cn.tolim.pudans.iam.authorization.dto.RoleRequest;
import cn.tolim.pudans.iam.authorization.dto.RoleSummary;
import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import cn.tolim.pudans.iam.authorization.exception.AuthorizationErrors;
import cn.tolim.pudans.iam.authorization.service.RoleService;
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
@Tag(name = "统一授权-角色管理")
@RestController
@RequestMapping(value = AuthorizationConstants.AUTHZ_API_PATH + "/roles",
        produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class RoleEndpoint {

    private final RoleService roleService;

    // Lists roles
    // Parameter status: String optional status filter
    // Parameter keyword: String optional name or code keyword
    // Returns: ApiRestResult<List<RoleSummary>>
    @Operation(summary = "查询角色列表")
    @GetMapping
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<List<RoleSummary>> list(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String keyword) {
        return ApiRestResult.ok(roleService.list(parseStatus(status), keyword));
    }

    // Returns a role
    // Parameter id: String role ID
    // Returns: ApiRestResult<RoleSummary>
    @Operation(summary = "查询角色详情")
    @GetMapping(value = "/{id}")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<RoleSummary> get(@PathVariable String id) {
        return ApiRestResult.ok(roleService.get(id));
    }

    // Creates a role
    // Parameter request: RoleRequest
    // Returns: ApiRestResult<RoleSummary>
    @Operation(summary = "创建角色")
    @PostMapping
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<RoleSummary> create(@Valid @RequestBody RoleRequest request) {
        return ApiRestResult.ok(roleService.create(request));
    }

    // Updates a role
    // Parameter id: String role ID
    // Parameter request: RoleRequest
    // Returns: ApiRestResult<RoleSummary>
    @Operation(summary = "更新角色")
    @PutMapping(value = "/{id}")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<RoleSummary> update(@PathVariable String id,
                                             @Valid @RequestBody RoleRequest request) {
        return ApiRestResult.ok(roleService.update(id, request));
    }

    // Deletes a role
    // Parameter id: String role ID
    // Returns: ApiRestResult<Void>
    @Operation(summary = "删除角色")
    @DeleteMapping(value = "/{id}")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<Void> delete(@PathVariable String id) {
        roleService.delete(id);
        return ApiRestResult.ok();
    }

    // Lists bindings of a role
    // Parameter id: String role ID
    // Returns: ApiRestResult<List<RoleBindingView>>
    @Operation(summary = "查询角色绑定")
    @GetMapping(value = "/{id}/bindings")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<List<RoleBindingView>> bindings(@PathVariable String id) {
        return ApiRestResult.ok(roleService.listBindingsByRole(id));
    }

    private static AuthorizationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        AuthorizationStatus parsed = AuthorizationStatus.getStatus(status.trim());
        if (parsed == null) {
            throw AuthorizationErrors.badRequest("Unsupported role status '" + status + "'");
        }
        return parsed;
    }
}