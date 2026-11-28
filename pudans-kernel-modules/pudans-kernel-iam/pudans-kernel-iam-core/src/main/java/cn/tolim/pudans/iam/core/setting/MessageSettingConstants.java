package cn.tolim.pudans.iam.core.setting;

import cn.tolim.pudans.iam.communal.constant.SettingConstants;

import static org.springframework.util.ResourceUtils.CLASSPATH_URL_PREFIX;


public final class MessageSettingConstants {
    // Message provider prefix
    public static final String MESSAGE_SETTING_PREFIX = "message.setting.";
    // Mail provider key
    public static final String MESSAGE_PROVIDER_EMAIL = MESSAGE_SETTING_PREFIX
            + "email_provider";

    // SMS verification service key
    public static final String MESSAGE_SMS_PROVIDER = MESSAGE_SETTING_PREFIX
            + "sms_provider";

    // Mail content path
    public final static String MAIL_CONTENT_PATH = CLASSPATH_URL_PREFIX
            + "mail/content/";

    // System email cache name
    public static final String SETTING_EMAIL_CACHE_NAME = SettingConstants.SETTING_CACHE_NAME
            + ":email";

    // System email template cache name
    public static final String SETTING_EMAIL_TEMPLATE_CACHE_NAME = SETTING_EMAIL_CACHE_NAME
            + ":template";

    // System email provider cache name
    public static final String SETTING_EMAIL_PROVIDER_CACHE_NAME = SETTING_EMAIL_CACHE_NAME
            + ":provider";

    // System SMS provider cache name
    public static final String SETTING_SMS_PROVIDER_CACHE_NAME = SettingConstants.SETTING_CACHE_NAME
            + ":sms:provider";

    // System SMS template cache name
    public static final String SETTING_SMS_TEMPLATE_CACHE_NAME = SettingConstants.SETTING_CACHE_NAME
            + "sms:template";
}
