package cn.tolim.pudans.iam.core.setting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class SecuritySettingConstants {
    // Security settings prefix
    public static final String SECURITY_PREFIX = "security.";

    // Security settings base configuration prefix
    public static final String SECURITY_BASIC_PREFIX = SECURITY_PREFIX
            + "basic.";
    // Session lifetime
    public static final String SECURITY_BASIC_SESSION_VALID_TIME = SECURITY_BASIC_PREFIX
            + "session_valid_time";
    // User concurrency limit
    public static final String SECURITY_SESSION_MAXIMUM = SECURITY_BASIC_PREFIX
            + "session_maximum";

    // Captcha lifetime
    public static final String VERIFY_CODE_VALID_TIME = SECURITY_BASIC_PREFIX
            + "verify_code_valid_time";
    // Remember-me lifetime
    public static final String SECURITY_BASIC_REMEMBER_ME_VALID_TIME = SECURITY_BASIC_PREFIX
            + "remember_me_valid_time";
    // Security defense policy
    public static final String SECURITY_DEFENSE_POLICY_PREFIX = SECURITY_PREFIX
            + "defense_strategy.";

    // Content Security Policy
    public static final String SECURITY_DEFENSE_POLICY_CONTENT_SECURITY_POLICY = SECURITY_DEFENSE_POLICY_PREFIX
            + "content";

    // Continuous login failure duration
    public static final String SECURITY_DEFENSE_POLICY_LOGIN_FAILURE_DURATION = SECURITY_DEFENSE_POLICY_PREFIX
            + "login_failure_duration";

    // Continuous login failure count
    public static final String SECURITY_DEFENSE_POLICY_FAILURE_COUNT = SECURITY_DEFENSE_POLICY_PREFIX
            + "login_failure_count";

    // Auto-unlock time (minutes)
    public static final String SECURITY_DEFENSE_POLICY_AUTO_UNLOCK_TIME = SECURITY_DEFENSE_POLICY_PREFIX
            + "auto_unlock_time";

    public static final Map<String, String> SECURITY_BASIC_DEFAULT_SETTINGS = new HashMap<>(
            16);
    public static final List<String> SECURITY_BASIC_KEY = new ArrayList<>();
    public static final List<String> SECURITY_DEFENSE_POLICY_KEY = new ArrayList<>();
    public static final Map<String, String> SECURITY_DEFENSE_POLICY_DEFAULT_SETTINGS = new HashMap<>(
            16);

    static {
        // Session lifetime (seconds), default 18000
        SECURITY_BASIC_DEFAULT_SETTINGS.put(SECURITY_BASIC_SESSION_VALID_TIME, "18000");
        // User concurrency limit, default 1
        SECURITY_BASIC_DEFAULT_SETTINGS.put(SECURITY_SESSION_MAXIMUM, "1");
        // Captcha lifetime (minutes)
        SECURITY_BASIC_DEFAULT_SETTINGS.put(VERIFY_CODE_VALID_TIME, "5");
        // Remember-me lifetime (seconds), default 7 days
        SECURITY_BASIC_DEFAULT_SETTINGS.put(SECURITY_BASIC_REMEMBER_ME_VALID_TIME, "604800");

    }

    static {
        // Session lifetime (seconds)
        SECURITY_BASIC_KEY.add(SECURITY_BASIC_SESSION_VALID_TIME);
        // User concurrency limit
        SECURITY_BASIC_KEY.add(SECURITY_SESSION_MAXIMUM);
        // Remember-me lifetime (seconds)
        SECURITY_BASIC_KEY.add(SECURITY_BASIC_REMEMBER_ME_VALID_TIME);
        // Captcha lifetime (seconds)
        SECURITY_BASIC_KEY.add(VERIFY_CODE_VALID_TIME);
    }

    static {
        // Content Security Policy
        SECURITY_DEFENSE_POLICY_KEY.add(SECURITY_DEFENSE_POLICY_CONTENT_SECURITY_POLICY);
        // Continuous login failure duration
        SECURITY_DEFENSE_POLICY_KEY.add(SECURITY_DEFENSE_POLICY_LOGIN_FAILURE_DURATION);
        // Continuous login failure count
        SECURITY_DEFENSE_POLICY_KEY.add(SECURITY_DEFENSE_POLICY_FAILURE_COUNT);
        // Auto-unlock time (minutes)
        SECURITY_DEFENSE_POLICY_KEY.add(SECURITY_DEFENSE_POLICY_AUTO_UNLOCK_TIME);
    }

    static {
        // Default Content Security Policy
        SECURITY_DEFENSE_POLICY_DEFAULT_SETTINGS.put(
                SECURITY_DEFENSE_POLICY_CONTENT_SECURITY_POLICY,
                "default-src 'self' data:; " + "frame-src 'self' login.dingtalk.com open.weixin.qq.com open.work.weixin.qq.com passport.feishu.cn data:; "
                        + "frame-ancestors 'self' iam.pudans.cn data:; "
                        + "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://storage.googleapis.com sf3-cn.feishucdn.com;"
                        + "style-src 'self' https://fonts.googleapis.com https://cdn.jsdelivr.net 'unsafe-inline'; "
                        + "img-src 'self' https://img.alicdn.com https://static-legacy.dingtalk.com  https://joeschmoe.io https://api.multiavatar.com blob: data:; "
                        + "font-src 'self' https://fonts.gstatic.com data:; "
                        + "worker-src 'self' https://storage.googleapis.com blob:; ");
        // Continuous login failure duration
        SECURITY_DEFENSE_POLICY_DEFAULT_SETTINGS.put(SECURITY_DEFENSE_POLICY_LOGIN_FAILURE_DURATION,
                "5");
        // Continuous login failure count
        SECURITY_DEFENSE_POLICY_DEFAULT_SETTINGS.put(SECURITY_DEFENSE_POLICY_FAILURE_COUNT, "3");
        // Auto-unlock time (minutes)
        SECURITY_DEFENSE_POLICY_DEFAULT_SETTINGS.put(SECURITY_DEFENSE_POLICY_AUTO_UNLOCK_TIME, "3");
    }
}
