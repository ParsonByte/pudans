package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.auth.ForgotPasswordRequest;
import cn.tolim.pudans.contract.iam.dto.auth.ForgotPasswordResponse;
import cn.tolim.pudans.contract.iam.dto.auth.PasswordResetRequest;
import cn.tolim.pudans.contract.iam.dto.auth.PasswordResetResponse;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.core.auth.password.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "IAM 登录认证")
@RestController
public class IamPasswordResetEndpoint {

    private final PasswordResetService passwordResetService;

    public IamPasswordResetEndpoint(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    // Sends a password reset verification code
    // Parameter request: ForgotPasswordRequest
    // Returns: ForgotPasswordResponse
    @Operation(summary = "发送密码重置验证码", operationId = "forgotPassword")
    @PostMapping(path = IamAuthApiPaths.FORGOT_PASSWORD)
    public ForgotPasswordResponse forgot(@RequestBody ForgotPasswordRequest request) {
        return passwordResetService.request(request);
    }

    // Verifies a reset code and replaces the account password
    // Parameter request: PasswordResetRequest
    // Returns: PasswordResetResponse
    @Operation(summary = "重置密码", operationId = "resetPassword")
    @PostMapping(path = IamAuthApiPaths.RESET_PASSWORD)
    public PasswordResetResponse reset(@RequestBody PasswordResetRequest request) {
        return passwordResetService.reset(request);
    }
}