package cn.tolim.pudans.iam.authorization.endpoint;

import cn.tolim.pudans.iam.authorization.constant.AuthorizationConstants;
import cn.tolim.pudans.iam.authorization.dto.PolicyAttachmentRequest;
import cn.tolim.pudans.iam.authorization.dto.PolicyAttachmentView;
import cn.tolim.pudans.iam.authorization.enums.PrincipalType;
import cn.tolim.pudans.iam.authorization.exception.AuthorizationErrors;
import cn.tolim.pudans.iam.authorization.service.EnumSupport;
import cn.tolim.pudans.iam.authorization.service.PolicyAttachmentService;
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
@Tag(name = "统一授权-策略绑定")
@RestController
@RequestMapping(value = AuthorizationConstants.AUTHZ_API_PATH + "/policy-attachments",
        produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class PolicyAttachmentEndpoint {

    private final PolicyAttachmentService policyAttachmentService;

    // Lists policy attachments by policy or principal
    // Parameter policyId: String optional policy ID filter
    // Parameter principalType: String optional principal type filter
    // Parameter principalId: String optional principal ID filter
    // Returns: ApiRestResult<List<PolicyAttachmentView>>
    @Operation(summary = "查询策略绑定")
    @GetMapping
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<List<PolicyAttachmentView>> list(
            @RequestParam(required = false) String policyId,
            @RequestParam(required = false) String principalType,
            @RequestParam(required = false) String principalId) {
        if (policyId != null && !policyId.isBlank()) {
            return ApiRestResult.ok(policyAttachmentService.listByPolicy(policyId.trim()));
        }
        if (principalType != null && !principalType.isBlank()
                && principalId != null && !principalId.isBlank()) {
            PrincipalType type = EnumSupport.principalType(principalType);
            return ApiRestResult.ok(policyAttachmentService.listByPrincipal(type, principalId.trim()));
        }
        throw AuthorizationErrors.badRequest(
                "policyId or both principalType and principalId must be provided");
    }

    // Attaches a policy to a principal
    // Parameter request: PolicyAttachmentRequest
    // Returns: ApiRestResult<PolicyAttachmentView>
    @Operation(summary = "绑定策略")
    @PostMapping
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<PolicyAttachmentView> attach(
            @Valid @RequestBody PolicyAttachmentRequest request) {
        return ApiRestResult.ok(policyAttachmentService.attach(request));
    }

    // Removes a policy attachment
    // Parameter id: String attachment ID
    // Returns: ApiRestResult<Void>
    @Operation(summary = "解绑策略")
    @DeleteMapping(value = "/{id}")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<Void> detach(@PathVariable String id) {
        policyAttachmentService.detach(id);
        return ApiRestResult.ok();
    }
}