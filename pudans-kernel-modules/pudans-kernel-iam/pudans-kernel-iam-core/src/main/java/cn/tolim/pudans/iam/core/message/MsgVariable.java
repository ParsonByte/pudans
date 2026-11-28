package cn.tolim.pudans.iam.core.message;

import java.io.Serializable;


public final class MsgVariable implements Serializable {

    // Client name
    public static final String CLIENT_NAME = "client_name";
    // Time point
    public static final String TIME = "time";
    // Client description
    public static final String CLIENT_DESCRIPTION = "client_description";
    // User email
    public static final String USER_EMAIL = "user_email";
    // Captcha
    public static final String VERIFY_CODE = "verify_code";

    // Remaining days
    public static final String EXPIRE_DAYS = "expire_days";

    // Password
    public static final String PASSWORD = "password";

    public static final String TEST = "test";

    // Expiration time key
    public static final String EXPIRE_TIME_KEY = "expire_time";
    // Send interval
    public static final int TIME_TO_LIVE = 1;
}
