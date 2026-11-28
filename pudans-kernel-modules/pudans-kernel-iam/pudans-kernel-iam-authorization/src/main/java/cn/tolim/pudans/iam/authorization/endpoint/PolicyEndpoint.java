package cn.tolim.pudans.iam.authorization.endpoint;

import cn.tolim.pudans.iam.authorization.constant.AuthorizationConstants;
import cn.tolim.pudans.iam.authorization.dto.PolicyDetail;
import cn.tolim.pudans.iam.authorization.dto.PolicyDocument;
import cn.tolim.pudans.iam.authorization.dto.PolicyRequest;
import cn.tolim.pudans.iam.authorization.dto.PolicySimulationRequest;
import cn.tolim.pudans.iam.authorization.dto.PolicySimulationResponse;
import cn.tolim.pudans.iam.authorization.dto.PolicySummary;
import cn.tolim.pudans.iam.authorization.dto.PolicyValidationResult;
import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import cn.tolim.pudans.iam.authorization.enums.PolicyType;
import cn.tolim.pudans.iam.authorization.exception.AuthorizationErrors;
import cn.tolim.pudans.iam.authorization.service.AuthorizationService;
import cn.tolim.pudans.iam.authorization.service.PolicyDocumentService;
import cn.tolim.pudans.iam.authorization.service.PolicyService;
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
@Tag(name = "统一授权-策略管理")
@RestController
@RequestMapping(value = AuthorizationConstants.AUTHZ_API_PATH + "/policies",
        produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class PolicyEndpoint {

    private final PolicyService policyService;
    private final PolicyDocumentService policyDocumentService;
    private final AuthorizationService authorizationService;

    // Lists policies
    // Parameter type: String optional policy type filter
    // Parameter status: String optional status filter
    // Parameter keyword: String optional name or code keyword
    // Returns: ApiRestResult<List<PolicySummary>>
    @Operation(summary = "查询策略列表")
    @GetMapping
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<List<PolicySummary>> list(@RequestParam(required = false) String type,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String keyword) {
        return ApiRestResult.ok(policyService.list(parseType(type), parseStatus(status), keyword));
    }

    // Returns a policy detail
    // Parameter id: String policy ID
    // Returns: ApiRestResult<PolicyDetail>
    @Operation(summary = "查询策略详情")
    @GetMapping(value = "/{id}")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<PolicyDetail> get(@PathVariable String id) {
        return ApiRestResult.ok(policyService.get(id));
    }

    // Creates a policy
    // Parameter request: PolicyRequest
    // Returns: ApiRestResult<PolicyDetail>
    @Operation(summary = "创建策略")
    @PostMapping
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<PolicyDetail> create(@Valid @RequestBody PolicyRequest request) {
        return ApiRestResult.ok(policyService.create(request));
    }

    // Updates a policy
    // Parameter id: String policy ID
    // Parameter request: PolicyRequest
    // Returns: ApiRestResult<PolicyDetail>
    @Operation(summary = "更新策略")
    @PutMapping(value = "/{id}")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<PolicyDetail> update(@PathVariable String id,
                                              @Valid @RequestBody PolicyRequest request) {
        return ApiRestResult.ok(policyService.update(id, request));
    }

    // Deletes a policy and its attachments
    // Parameter id: String policy ID
    // Returns: ApiRestResult<Void>
    @Operation(summary = "删除策略")
    @DeleteMapping(value = "/{id}")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<Void> delete(@PathVariable String id) {
        policyService.delete(id);
        return ApiRestResult.ok();
    }

    // Validates a policy document without saving it
    // Parameter document: PolicyDocument
    // Returns: ApiRestResult<PolicyValidationResult>
    @Operation(summary = "校验策略文档")
    @PostMapping(value = "/validate")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<PolicyValidationResult> validate(@RequestBody PolicyDocument document) {
        return ApiRestResult.ok(policyDocumentService.validate(document));
    }

    // Simulates policies against a hypothetical request
    // Parameter request: PolicySimulationRequest
    // Returns: ApiRestResult<PolicySimulationResponse>
    @Operation(summary = "模拟策略")
    @PostMapping(value = "/simulate")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<PolicySimulationResponse> simulate(
            @Valid @RequestBody PolicySimulationRequest request) {
        return ApiRestResult.ok(authorizationService.simulate(request));
    }

    private static PolicyType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        PolicyType parsed = PolicyType.getType(type.trim());
        if (parsed == null) {
            throw AuthorizationErrors.badRequest("Unsupported policy type '" + type + "'");
        }
        return parsed;
    }

    private static AuthorizationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        AuthorizationStatus parsed = AuthorizationStatus.getStatus(status.trim());
        if (parsed == null) {
            throw AuthorizationErrors.badRequest("Unsupported policy status '" + status + "'");
        }
        return parsed;
    }
}