package cn.tolim.pudans.contract.notification;

// Shared constants for notification service-to-service contracts
public final class NotificationContractConstants {

    public static final String SERVICE_NAME = "pudans-notification";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_SMS = "SMS";
    public static final String CHANNEL_FEISHU = "FEISHU";
    public static final String CHANNEL_DINGTALK = "DINGTALK";
    public static final String PROVIDER_TYPE_SMTP = "SMTP";
    public static final String PROVIDER_TYPE_ALIYUN_SMS = "ALIYUN_SMS";
    public static final String PROVIDER_TYPE_TENCENT_SMS = "TENCENT_SMS";
    public static final String PROVIDER_TYPE_QINIU_SMS = "QINIU_SMS";
    public static final String PROVIDER_TYPE_FEISHU = "FEISHU";
    public static final String PROVIDER_TYPE_DINGTALK = "DINGTALK";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";

    private NotificationContractConstants() {
    }
}
