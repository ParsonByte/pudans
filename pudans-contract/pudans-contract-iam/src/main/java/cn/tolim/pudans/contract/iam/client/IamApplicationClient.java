package cn.tolim.pudans.contract.iam.client;

import cn.tolim.pudans.contract.iam.IamContractConstants;
import cn.tolim.pudans.contract.iam.IamContractPaths;
import cn.tolim.pudans.contract.iam.dto.IamApiResponse;
import cn.tolim.pudans.contract.iam.dto.app.ApplicationAccessRequest;
import cn.tolim.pudans.contract.iam.dto.app.ApplicationAccessResponse;
import cn.tolim.pudans.contract.iam.dto.app.ApplicationRegistrationRequest;
import cn.tolim.pudans.contract.iam.dto.app.ApplicationRegistrationResponse;
import cn.tolim.pudans.contract.iam.dto.app.ApplicationSummary;
import cn.tolim.pudans.contract.iam.dto.app.MyApplicationSummary;
import cn.tolim.pudans.contract.iam.dto.authz.ResourceTypeSummary;
import cn.tolim.pudans.contract.iam.dto.authz.ServiceActionSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

// IAM application registration and access contract
@FeignClient(name = IamContractConstants.SERVICE_NAME, contextId = "iamApplicationClient",
        path = IamContractPaths.BASE)
public interface IamApplicationClient {

    // Get an application registration by code
    // Parameter appCode: String
    // Returns: IamApiResponse<ApplicationSummary>
    @GetMapping(IamContractPaths.APP_DETAIL_SUFFIX)
    IamApiResponse<ApplicationSummary> getApplication(@PathVariable("appCode") String appCode);

    // Check whether a user may access an application
    // Parameter appCode: String
    // Parameter request: ApplicationAccessRequest
    // Returns: IamApiResponse<ApplicationAccessResponse>
    @GetMapping(IamContractPaths.APP_ACCESS_SUFFIX)
    IamApiResponse<ApplicationAccessResponse> checkAccess(
            @PathVariable("appCode") String appCode,
            @SpringQueryMap ApplicationAccessRequest request);

    // List applications accessible by the current user
    // Parameter sid: String
    // Returns: IamApiResponse<List<MyApplicationSummary>>
    @GetMapping(IamContractPaths.ME_APPS_SUFFIX)
    IamApiResponse<List<MyApplicationSummary>> listMyApplications(
            @RequestHeader(value = IamContractConstants.HEADER_SESSION_ID,
                    required = false) String sid);

    // Register or update an application declaration
    // Parameter registrationToken: String
    // Parameter request: ApplicationRegistrationRequest
    // Returns: IamApiResponse<ApplicationRegistrationResponse>
    @PostMapping(IamContractPaths.APP_REGISTER_SUFFIX)
    IamApiResponse<ApplicationRegistrationResponse> registerApplication(
            @RequestHeader(value = IamContractConstants.HEADER_REGISTRATION_TOKEN,
                    required = false) String registrationToken,
            @RequestBody ApplicationRegistrationRequest request);

    // List declared actions of an application
    // Parameter appCode: String
    // Returns: IamApiResponse<List<ServiceActionSummary>>
    @GetMapping(IamContractPaths.APP_ACTIONS_SUFFIX)
    IamApiResponse<List<ServiceActionSummary>> listApplicationActions(
            @PathVariable("appCode") String appCode);

    // List declared resource types of an application
    // Parameter appCode: String
    // Returns: IamApiResponse<List<ResourceTypeSummary>>
    @GetMapping(IamContractPaths.APP_RESOURCE_TYPES_SUFFIX)
    IamApiResponse<List<ResourceTypeSummary>> listApplicationResourceTypes(
            @PathVariable("appCode") String appCode);
}