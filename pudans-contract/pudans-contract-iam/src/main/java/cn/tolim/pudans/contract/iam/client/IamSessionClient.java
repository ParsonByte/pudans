package cn.tolim.pudans.contract.iam.client;

import cn.tolim.pudans.contract.iam.IamContractConstants;
import cn.tolim.pudans.contract.iam.IamContractPaths;
import cn.tolim.pudans.contract.iam.dto.IamApiResponse;
import cn.tolim.pudans.contract.iam.dto.session.SessionRevokeRequest;
import cn.tolim.pudans.contract.iam.dto.session.SessionRevokeResponse;
import cn.tolim.pudans.contract.iam.dto.session.SessionRevokeUserRequest;
import cn.tolim.pudans.contract.iam.dto.session.SessionRevokeUserResponse;
import cn.tolim.pudans.contract.iam.dto.session.SessionSummary;
import cn.tolim.pudans.contract.iam.dto.session.SessionTouchRequest;
import cn.tolim.pudans.contract.iam.dto.session.SessionTouchResponse;
import cn.tolim.pudans.contract.iam.dto.session.SessionValidateRequest;
import cn.tolim.pudans.contract.iam.dto.session.SessionValidateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

// IAM global login session contract
@FeignClient(name = IamContractConstants.SERVICE_NAME, contextId = "iamSessionClient",
        path = IamContractPaths.BASE)
public interface IamSessionClient {

    // Validate a global login session
    // Parameter request: SessionValidateRequest
    // Returns: IamApiResponse<SessionValidateResponse>
    @PostMapping(IamContractPaths.SESSION_VALIDATE_SUFFIX)
    IamApiResponse<SessionValidateResponse> validate(@RequestBody SessionValidateRequest request);

    // Touch a global login session
    // Parameter request: SessionTouchRequest
    // Returns: IamApiResponse<SessionTouchResponse>
    @PostMapping(IamContractPaths.SESSION_TOUCH_SUFFIX)
    IamApiResponse<SessionTouchResponse> touch(@RequestBody SessionTouchRequest request);

    // Revoke one global login session
    // Parameter request: SessionRevokeRequest
    // Returns: IamApiResponse<SessionRevokeResponse>
    @PostMapping(IamContractPaths.SESSION_REVOKE_SUFFIX)
    IamApiResponse<SessionRevokeResponse> revoke(@RequestBody SessionRevokeRequest request);

    // Revoke every global login session of a user
    // Parameter request: SessionRevokeUserRequest
    // Returns: IamApiResponse<SessionRevokeUserResponse>
    @PostMapping(IamContractPaths.SESSION_REVOKE_USER_SUFFIX)
    IamApiResponse<SessionRevokeUserResponse> revokeUser(
            @RequestBody SessionRevokeUserRequest request);

    // Get a global login session
    // Parameter sid: String
    // Returns: IamApiResponse<SessionSummary>
    @GetMapping(IamContractPaths.SESSION_DETAIL_SUFFIX)
    IamApiResponse<SessionSummary> getSession(@PathVariable("sid") String sid);

    // List global login sessions of a user
    // Parameter userId: String
    // Returns: IamApiResponse<List<SessionSummary>>
    @GetMapping(IamContractPaths.SESSION_USER_SUFFIX)
    IamApiResponse<List<SessionSummary>> listUserSessions(@PathVariable("userId") String userId);
}