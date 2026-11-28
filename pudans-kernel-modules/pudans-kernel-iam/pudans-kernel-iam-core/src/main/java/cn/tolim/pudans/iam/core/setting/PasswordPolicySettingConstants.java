package cn.tolim.pudans.iam.core.setting;

import cn.tolim.pudans.iam.infra.security.password.enums.PasswordComplexityRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class PasswordPolicySettingConstants {
    // Password rule prefix
    public static final String PASSWORD_POLICY_PREFIX = "password.policy.";
    // Maximum length
    public static final String PASSWORD_POLICY_BIGGEST_LENGTH = PASSWORD_POLICY_PREFIX
            + "password_biggest_length";

    // Minimum length
    public static final String PASSWORD_POLICY_LEAST_LENGTH = PASSWORD_POLICY_PREFIX
            + "password_least_length";

    // Complexity
    public static final String PASSWORD_POLICY_COMPLEXITY = PASSWORD_POLICY_PREFIX
            + "password_complexity";
    // Weak password check
    public static final String PASSWORD_POLICY_WEAK_PASSWORD_CHECK = PASSWORD_POLICY_PREFIX
            + "weak_password_check";

    // Account information check (disallow username, phone number, email prefix, and pinyin name)
    public static final String PASSWORD_POLICY_ACCOUNT_CHECK = PASSWORD_POLICY_PREFIX
            + "account_info_check";
    // Maximum number of identical consecutive characters
    public static final String PASSWORD_POLICY_NOT_SAME_CHARS = PASSWORD_POLICY_PREFIX
            + "not_same_chars";
    // Password history check (stores password history so old passwords cannot be reused consecutively)
    public static final String PASSWORD_POLICY_HISTORY_PASSWORD_CHECK = PASSWORD_POLICY_PREFIX
            + "history_password_check";
    // Password history count (must be between 1 and 10)
    public static final String PASSWORD_POLICY_HISTORY_PASSWORD_CHECK_COUNT = PASSWORD_POLICY_PREFIX
            + "history_password_check_count";

    // Invalid sequence check to reject keyboard, alphabetic, or numeric sequences
    public static final String PASSWORD_POLICY_ILLEGAL_SEQUENCE_CHECK = PASSWORD_POLICY_PREFIX
            + "illegal_sequence_check";

    // Password expiration days
    public static final String PASSWORD_POLICY_VALID_DAYS = PASSWORD_POLICY_PREFIX
            + "password_valid_days";
    // Password expiration reminder time
    public static final String PASSWORD_POLICY_VALID_WARN_BEFORE_DAYS = PASSWORD_POLICY_PREFIX
            + "password_valid_warn_before_days";

    // Custom weak password
    public static final String PASSWORD_POLICY_CUSTOM_WEAK_PASSWORD = PASSWORD_POLICY_PREFIX
            + "custom_weak_password_lib";

    // Password rule key
    public static final List<String> PASSWORD_POLICY_KEYS = new ArrayList<>();
    // Password rule default
    public static final Map<String, String> PASSWORD_POLICY_DEFAULT_SETTINGS = new HashMap<>(16);

    static {
        // Maximum length
        PASSWORD_POLICY_KEYS.add(PASSWORD_POLICY_BIGGEST_LENGTH);
        // Minimum length
        PASSWORD_POLICY_KEYS.add(PASSWORD_POLICY_LEAST_LENGTH);
        // Complexity
        PASSWORD_POLICY_KEYS.add(PASSWORD_POLICY_COMPLEXITY);
        // Weak password check
        PASSWORD_POLICY_KEYS.add(PASSWORD_POLICY_WEAK_PASSWORD_CHECK);
        // Weak password dictionary
        PASSWORD_POLICY_KEYS.add(PASSWORD_POLICY_CUSTOM_WEAK_PASSWORD);
        // Account information check
        PASSWORD_POLICY_KEYS.add(PASSWORD_POLICY_ACCOUNT_CHECK);
        // Identical character count
        PASSWORD_POLICY_KEYS.add(PASSWORD_POLICY_NOT_SAME_CHARS);
        // Password history check
        PASSWORD_POLICY_KEYS.add(PASSWORD_POLICY_HISTORY_PASSWORD_CHECK);
        // Password expiration days
        PASSWORD_POLICY_KEYS.add(PASSWORD_POLICY_VALID_DAYS);
        // Password expiration reminder time
        PASSWORD_POLICY_KEYS.add(PASSWORD_POLICY_VALID_WARN_BEFORE_DAYS);
    }

    static {
        // Maximum length
        PASSWORD_POLICY_DEFAULT_SETTINGS.put(PASSWORD_POLICY_BIGGEST_LENGTH, "20");
        // Minimum length
        PASSWORD_POLICY_DEFAULT_SETTINGS.put(PASSWORD_POLICY_LEAST_LENGTH, "6");
        // Complexity
        PASSWORD_POLICY_DEFAULT_SETTINGS.put(PASSWORD_POLICY_COMPLEXITY,
                PasswordComplexityRule.NONE.getCode());
        // Weak password check
        PASSWORD_POLICY_DEFAULT_SETTINGS.put(PASSWORD_POLICY_WEAK_PASSWORD_CHECK, "false");
        // Weak password dictionary
        PASSWORD_POLICY_DEFAULT_SETTINGS.put(PASSWORD_POLICY_CUSTOM_WEAK_PASSWORD, "");
        // Account information check
        PASSWORD_POLICY_DEFAULT_SETTINGS.put(PASSWORD_POLICY_ACCOUNT_CHECK, "false");
        // Maximum number of identical consecutive characters
        PASSWORD_POLICY_DEFAULT_SETTINGS.put(PASSWORD_POLICY_NOT_SAME_CHARS, "3");
        // Password history check
        PASSWORD_POLICY_DEFAULT_SETTINGS.put(PASSWORD_POLICY_HISTORY_PASSWORD_CHECK, "true");
        // Password history check count range
        PASSWORD_POLICY_DEFAULT_SETTINGS.put(PASSWORD_POLICY_HISTORY_PASSWORD_CHECK_COUNT, "5");
        // Invalid sequence check
        PASSWORD_POLICY_DEFAULT_SETTINGS.put(PASSWORD_POLICY_ILLEGAL_SEQUENCE_CHECK, "false");
        // Password expiration days
        PASSWORD_POLICY_DEFAULT_SETTINGS.put(PASSWORD_POLICY_VALID_DAYS, "120");
        // Password expiration reminder time
        PASSWORD_POLICY_DEFAULT_SETTINGS.put(PASSWORD_POLICY_VALID_WARN_BEFORE_DAYS, "7");
    }
}
