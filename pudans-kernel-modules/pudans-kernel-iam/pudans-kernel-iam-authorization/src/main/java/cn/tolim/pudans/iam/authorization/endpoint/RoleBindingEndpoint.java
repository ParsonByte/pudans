package cn.tolim.pudans.iam.authorization.endpoint;

import cn.tolim.pudans.iam.authorization.constant.AuthorizationConstants;
import cn.tolim.pudans.iam.authorization.dto.RoleBindingRequest;
import cn.tolim.pudans.iam.authorization.dto.RoleBindingView;
import cn.tolim.pudans.iam.authorization.enums.PrincipalType;
import cn.tolim.pudans.iam.authorization.exception.AuthorizationErrors;
import cn.tolim.pudans.iam.authorization.service.EnumSupport;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Validated
@Tag(name = "统一授权-角色绑定")
@RestController
@RequestMapping(value = AuthorizationConstants.AUTHZ_API_PATH + "/role-bindings",
        produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class RoleBindingEndpoint {

    private final RoleService roleService;

    // Lists role bindings by role or principal
    // Parameter roleId: String optional role ID filter
    // Parameter principalType: String optional principal type filter
    // Parameter principalId: String optional principal ID filter
    // Returns: ApiRestResult<List<RoleBindingView>>
    @Operation(summary = "查询角色绑定")
    @GetMapping
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<List<RoleBindingView>> list(@RequestParam(required = false) String roleId,
                                                     @RequestParam(required = false) String principalType,
                                                     @RequestParam(required = false) String principalId) {
        if (roleId != null && !roleId.isBlank()) {
            return ApiRestResult.ok(roleService.listBindingsByRole(roleId.trim()));
        }
        if (principalType != null && !principalType.isBlank()
                && principalId != null && !principalId.isBlank()) {
            PrincipalType type = EnumSupport.principalType(principalType);
            return ApiRestResult.ok(roleService.listBindingsByPrincipal(type, principalId.trim()));
        }
        throw AuthorizationErrors.badRequest(
                "roleId or both principalType and principalId must be provided");
    }

    // Binds a role to a principal
    // Parameter request: RoleBindingRequest
    // Returns: ApiRestResult<RoleBindingView>
    @Operation(summary = "绑定角色")
    @PostMapping
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<RoleBindingView> bind(@Valid @RequestBody RoleBindingRequest request) {
        return ApiRestResult.ok(roleService.bind(request));
    }

    // Removes a role binding
    // Parameter id: String binding ID
    // Returns: ApiRestResult<Void>
    @Operation(summary = "解绑角色")
    @DeleteMapping(value = "/{id}")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<Void> unbind(@PathVariable String id) {
        roleService.unbind(id);
        return ApiRestResult.ok();
    }
}