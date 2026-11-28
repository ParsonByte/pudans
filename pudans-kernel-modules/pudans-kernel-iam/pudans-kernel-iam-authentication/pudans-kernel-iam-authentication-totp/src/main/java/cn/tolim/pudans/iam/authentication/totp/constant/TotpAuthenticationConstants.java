package cn.tolim.pudans.iam.authentication.totp.constant;

import cn.tolim.pudans.iam.infra.constant.IamConstants;

import static cn.tolim.pudans.iam.infra.security.constant.SecurityConstants.LOGIN_PATH;

// Paths and parameter names of TOTP authentication
public final class TotpAuthenticationConstants {

    // Identity provider type value used by OTP tokens
    public static final String PROVIDER_TYPE = "totp";

    // TOTP login endpoint relative to the IAM API prefix
    public static final String OTP_LOGIN = LOGIN_PATH + "/otp/totp";

    // Username request parameter
    public static final String USERNAME_PARAMETER = "username";

    // One-time code request parameter
    public static final String CODE_PARAMETER = "code";

    // TOTP MFA management API base path
    public static final String API_PATH = IamConstants.API_PREFIX + "/auth/mfa/totp";

    private TotpAuthenticationConstants() {
    }
}