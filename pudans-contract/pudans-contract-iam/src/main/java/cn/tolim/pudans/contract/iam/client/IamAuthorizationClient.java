package cn.tolim.pudans.contract.iam.client;

import cn.tolim.pudans.contract.iam.IamContractConstants;
import cn.tolim.pudans.contract.iam.IamContractPaths;
import cn.tolim.pudans.contract.iam.dto.IamApiResult;
import cn.tolim.pudans.contract.iam.dto.authz.AuthzLogView;
import cn.tolim.pudans.contract.iam.dto.authz.EffectivePermissionResponse;
import cn.tolim.pudans.contract.iam.dto.authz.PolicyEvaluateRequest;
import cn.tolim.pudans.contract.iam.dto.authz.PolicyEvaluateResponse;
import cn.tolim.pudans.contract.iam.dto.authz.PolicySimulationRequest;
import cn.tolim.pudans.contract.iam.dto.authz.PolicySimulationResponse;
import cn.tolim.pudans.contract.iam.dto.authz.ResourceTypeSummary;
import cn.tolim.pudans.contract.iam.dto.authz.ServiceActionSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

// IAM CAM style authorization contract
@FeignClient(name = IamContractConstants.SERVICE_NAME, contextId = "iamAuthorizationClient",
        path = IamContractPaths.BASE)
public interface IamAuthorizationClient {

    // Evaluate one authorization request
    // Parameter request: PolicyEvaluateRequest
    // Returns: IamApiResult<PolicyEvaluateResponse>
    @PostMapping(IamContractPaths.AUTHZ_EVALUATE_SUFFIX)
    IamApiResult<PolicyEvaluateResponse> evaluate(@RequestBody PolicyEvaluateRequest request);

    // Evaluate a batch of authorization requests
    // Parameter requests: List<PolicyEvaluateRequest>
    // Returns: IamApiResult<List<PolicyEvaluateResponse>>
    @PostMapping(IamContractPaths.AUTHZ_EVALUATE_BATCH_SUFFIX)
    IamApiResult<List<PolicyEvaluateResponse>> evaluateBatch(
            @RequestBody List<PolicyEvaluateRequest> requests);

    // Simulate policies against a hypothetical request
    // Parameter request: PolicySimulationRequest
    // Returns: IamApiResult<PolicySimulationResponse>
    @PostMapping(IamContractPaths.AUTHZ_SIMULATE_SUFFIX)
    IamApiResult<PolicySimulationResponse> simulate(@RequestBody PolicySimulationRequest request);

    // Get the effective roles and policies of a principal
    // Parameter principalType: String
    // Parameter principalId: String
    // Parameter appCode: String
    // Returns: IamApiResult<EffectivePermissionResponse>
    @GetMapping(IamContractPaths.AUTHZ_EFFECTIVE_PERMISSIONS_SUFFIX)
    IamApiResult<EffectivePermissionResponse> getEffectivePermissions(
            @PathVariable("principalType") String principalType,
            @PathVariable("principalId") String principalId,
            @RequestParam(value = "appCode", required = false) String appCode);

    // List authorization decision logs
    // Parameter principalType: String
    // Parameter principalId: String
    // Parameter appCode: String
    // Parameter decision: String
    // Parameter limit: Integer
    // Returns: IamApiResult<List<AuthzLogView>>
    @GetMapping(IamContractPaths.AUTHZ_LOGS_SUFFIX)
    IamApiResult<List<AuthzLogView>> listDecisionLogs(
            @RequestParam(value = "principalType", required = false) String principalType,
            @RequestParam(value = "principalId", required = false) String principalId,
            @RequestParam(value = "appCode", required = false) String appCode,
            @RequestParam(value = "decision", required = false) String decision,
            @RequestParam(value = "limit", defaultValue = "100") Integer limit);

    // List registered actions of an application
    // Parameter appCode: String
    // Returns: IamApiResult<List<ServiceActionSummary>>
    @GetMapping(IamContractPaths.AUTHZ_ACTIONS_SUFFIX)
    IamApiResult<List<ServiceActionSummary>> listActions(@RequestParam("appCode") String appCode);

    // List registered resource types of an application
    // Parameter appCode: String
    // Returns: IamApiResult<List<ResourceTypeSummary>>
    @GetMapping(IamContractPaths.AUTHZ_RESOURCE_TYPES_SUFFIX)
    IamApiResult<List<ResourceTypeSummary>> listResourceTypes(@RequestParam("appCode") String appCode);
}