package cn.tolim.pudans.iam.authorization.endpoint;

import cn.tolim.pudans.iam.authorization.constant.AuthorizationConstants;
import cn.tolim.pudans.iam.authorization.dto.AuthzLogView;
import cn.tolim.pudans.iam.authorization.dto.EffectivePermissionResponse;
import cn.tolim.pudans.iam.authorization.dto.PolicyEvaluateRequest;
import cn.tolim.pudans.iam.authorization.dto.PolicyEvaluateResponse;
import cn.tolim.pudans.iam.authorization.dto.PolicySimulationRequest;
import cn.tolim.pudans.iam.authorization.dto.PolicySimulationResponse;
import cn.tolim.pudans.iam.authorization.service.AuthorizationService;
import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Validated
@Tag(name = "统一授权")
@RestController
@RequestMapping(value = AuthorizationConstants.AUTHZ_API_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class AuthorizationEndpoint {

    private final AuthorizationService authorizationService;

    // Evaluates a single authorization request
    // Parameter request: PolicyEvaluateRequest
    // Returns: ApiRestResult<PolicyEvaluateResponse>
    @Operation(summary = "鉴权评估")
    @PostMapping(value = "/evaluate")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<PolicyEvaluateResponse> evaluate(
            @Valid @RequestBody PolicyEvaluateRequest request) {
        return ApiRestResult.ok(authorizationService.evaluate(request));
    }

    // Evaluates a batch of authorization requests
    // Parameter requests: List<PolicyEvaluateRequest>
    // Returns: ApiRestResult<List<PolicyEvaluateResponse>>
    @Operation(summary = "批量鉴权评估")
    @PostMapping(value = "/evaluate/batch")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<List<PolicyEvaluateResponse>> evaluateBatch(
            @RequestBody List<PolicyEvaluateRequest> requests) {
        return ApiRestResult.ok(authorizationService.evaluateBatch(requests));
    }

    // Simulates policies against a hypothetical request
    // Parameter request: PolicySimulationRequest
    // Returns: ApiRestResult<PolicySimulationResponse>
    @Operation(summary = "策略模拟器")
    @PostMapping(value = "/simulate")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<PolicySimulationResponse> simulate(
            @Valid @RequestBody PolicySimulationRequest request) {
        return ApiRestResult.ok(authorizationService.simulate(request));
    }

    // Returns the effective roles and policies of a principal
    // Parameter principalType: String principal type code
    // Parameter principalId: String principal ID
    // Parameter appCode: String optional application code filter
    // Returns: ApiRestResult<EffectivePermissionResponse>
    @Operation(summary = "查询主体有效权限")
    @GetMapping(value = "/principals/{principalType}/{principalId}/effective-permissions")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<EffectivePermissionResponse> effectivePermissions(
            @PathVariable String principalType, @PathVariable String principalId,
            @RequestParam(required = false) String appCode) {
        return ApiRestResult.ok(authorizationService.effectivePermissions(principalType,
                principalId, appCode));
    }

    // Lists authorization decision logs
    // Parameter principalType: String optional principal type filter
    // Parameter principalId: String optional principal ID filter
    // Parameter appCode: String optional application filter
    // Parameter decision: String optional decision filter
    // Parameter limit: int maximum number of entries
    // Returns: ApiRestResult<List<AuthzLogView>>
    @Operation(summary = "查询鉴权决策日志")
    @GetMapping(value = "/logs")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<List<AuthzLogView>> logs(
            @RequestParam(required = false) String principalType,
            @RequestParam(required = false) String principalId,
            @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String decision,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiRestResult.ok(authorizationService.logs(principalType, principalId, appCode,
                decision, limit));
    }
}