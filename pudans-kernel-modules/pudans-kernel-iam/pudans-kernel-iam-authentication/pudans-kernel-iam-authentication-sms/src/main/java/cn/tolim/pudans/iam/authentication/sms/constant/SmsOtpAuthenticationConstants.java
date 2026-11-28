package cn.tolim.pudans.iam.authentication.sms.constant;

import static cn.tolim.pudans.iam.infra.security.constant.SecurityConstants.LOGIN_PATH;


public final class SmsOtpAuthenticationConstants {

    public static final String RECIPIENT_KEY = "recipient";
    public static final String CODE_KEY = "code";
    // Captcha login path
    public static final String OTP_LOGIN = LOGIN_PATH + "/otp";
}
