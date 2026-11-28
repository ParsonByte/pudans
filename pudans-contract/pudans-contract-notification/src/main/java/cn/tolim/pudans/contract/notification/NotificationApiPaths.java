package cn.tolim.pudans.contract.notification;

// Notification service API paths
public final class NotificationApiPaths {

    public static final String BASE = "/apis/pudans.tolim.cn/v1alpha1/notification";

    public static final String SEND_SUFFIX = "/send";
    public static final String SEND = BASE + SEND_SUFFIX;

    public static final String SEND_BATCH_SUFFIX = "/send/batch";
    public static final String SEND_BATCH = BASE + SEND_BATCH_SUFFIX;

    public static final String RECORDS_SUFFIX = "/records";
    public static final String RECORDS = BASE + RECORDS_SUFFIX;

    public static final String RECORD_DETAIL_SUFFIX = "/records/{id}";
    public static final String RECORD_DETAIL = BASE + RECORD_DETAIL_SUFFIX;

    public static final String RECORD_RETRY_SUFFIX = "/records/{id}/retry";
    public static final String RECORD_RETRY = BASE + RECORD_RETRY_SUFFIX;

    public static final String DEAD_LETTERS_SUFFIX = "/dead-letters";
    public static final String DEAD_LETTERS = BASE + DEAD_LETTERS_SUFFIX;

    public static final String DEAD_LETTER_DETAIL_SUFFIX = "/dead-letters/{id}";
    public static final String DEAD_LETTER_DETAIL = BASE + DEAD_LETTER_DETAIL_SUFFIX;

    public static final String DEAD_LETTER_REPLAY_SUFFIX = "/dead-letters/{id}/replay";
    public static final String DEAD_LETTER_REPLAY = BASE + DEAD_LETTER_REPLAY_SUFFIX;

    public static final String STATS_SUFFIX = "/stats";
    public static final String STATS = BASE + STATS_SUFFIX;

    public static final String PROVIDERS_SUFFIX = "/providers";
    public static final String PROVIDERS = BASE + PROVIDERS_SUFFIX;

    public static final String PROVIDER_DETAIL_SUFFIX = "/providers/{id}";
    public static final String PROVIDER_DETAIL = BASE + PROVIDER_DETAIL_SUFFIX;

    public static final String PROVIDER_TEST_SUFFIX = "/providers/{id}/test";
    public static final String PROVIDER_TEST = BASE + PROVIDER_TEST_SUFFIX;

    public static final String TEMPLATES_SUFFIX = "/templates";
    public static final String TEMPLATES = BASE + TEMPLATES_SUFFIX;

    public static final String TEMPLATE_DETAIL_SUFFIX = "/templates/{id}";
    public static final String TEMPLATE_DETAIL = BASE + TEMPLATE_DETAIL_SUFFIX;

    private NotificationApiPaths() {
    }
}
