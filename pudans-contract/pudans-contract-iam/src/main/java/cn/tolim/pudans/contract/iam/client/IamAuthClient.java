package cn.tolim.pudans.contract.iam.client;

import cn.tolim.pudans.contract.iam.IamContractConstants;
import cn.tolim.pudans.contract.iam.IamContractPaths;
import cn.tolim.pudans.contract.iam.dto.IamApiResponse;
import cn.tolim.pudans.contract.iam.dto.auth.ForgotPasswordRequest;
import cn.tolim.pudans.contract.iam.dto.auth.ForgotPasswordResponse;
import cn.tolim.pudans.contract.iam.dto.auth.LoginConfigResponse;
import cn.tolim.pudans.contract.iam.dto.auth.LoginRequest;
import cn.tolim.pudans.contract.iam.dto.auth.LoginResponse;
import cn.tolim.pudans.contract.iam.dto.auth.LogoutRequest;
import cn.tolim.pudans.contract.iam.dto.auth.LogoutResponse;
import cn.tolim.pudans.contract.iam.dto.auth.MfaChallengeRequest;
import cn.tolim.pudans.contract.iam.dto.auth.MfaChallengeResponse;
import cn.tolim.pudans.contract.iam.dto.auth.MfaVerifyRequest;
import cn.tolim.pudans.contract.iam.dto.auth.PasswordResetRequest;
import cn.tolim.pudans.contract.iam.dto.auth.PasswordResetResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

// IAM authentication contract
@FeignClient(name = IamContractConstants.SERVICE_NAME, contextId = "iamAuthClient",
        path = IamContractPaths.BASE)
public interface IamAuthClient {

    // Password login
    // Parameter request: LoginRequest
    // Returns: IamApiResponse<LoginResponse>
    @PostMapping(IamContractPaths.AUTH_LOGIN_SUFFIX)
    IamApiResponse<LoginResponse> login(@RequestBody LoginRequest request);

    // Global logout
    // Parameter request: LogoutRequest
    // Returns: IamApiResponse<LogoutResponse>
    @PostMapping(IamContractPaths.AUTH_LOGOUT_SUFFIX)
    IamApiResponse<LogoutResponse> logout(@RequestBody LogoutRequest request);

    // Login page configuration
    // Parameter appCode: String
    // Returns: IamApiResponse<LoginConfigResponse>
    @GetMapping(IamContractPaths.AUTH_LOGIN_CONFIG_SUFFIX)
    IamApiResponse<LoginConfigResponse> getLoginConfig(
            @RequestParam(value = "appCode", required = false) String appCode);

    // Start an MFA challenge
    // Parameter request: MfaChallengeRequest
    // Returns: IamApiResponse<MfaChallengeResponse>
    @PostMapping(IamContractPaths.AUTH_MFA_CHALLENGE_SUFFIX)
    IamApiResponse<MfaChallengeResponse> challengeMfa(@RequestBody MfaChallengeRequest request);

    // Verify a pending MFA challenge
    // Parameter request: MfaVerifyRequest
    // Returns: IamApiResponse<LoginResponse>
    @PostMapping(IamContractPaths.AUTH_MFA_VERIFY_SUFFIX)
    IamApiResponse<LoginResponse> verifyMfa(@RequestBody MfaVerifyRequest request);

    // Start a forgot password flow
    // Parameter request: ForgotPasswordRequest
    // Returns: IamApiResponse<ForgotPasswordResponse>
    @PostMapping(IamContractPaths.AUTH_FORGOT_PASSWORD_SUFFIX)
    IamApiResponse<ForgotPasswordResponse> forgotPassword(
            @RequestBody ForgotPasswordRequest request);

    // Reset a password with a one-time token
    // Parameter request: PasswordResetRequest
    // Returns: IamApiResponse<PasswordResetResponse>
    @PostMapping(IamContractPaths.AUTH_RESET_PASSWORD_SUFFIX)
    IamApiResponse<PasswordResetResponse> resetPassword(@RequestBody PasswordResetRequest request);
}