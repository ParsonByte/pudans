package cn.tolim.pudans.contract.mq;

// Message center API paths
public final class MqApiPaths {

    public static final String BASE = "/apis/pudans.tolim.cn/v1alpha1/mq";

    public static final String MESSAGES_SUFFIX = "/messages";
    public static final String MESSAGES = BASE + MESSAGES_SUFFIX;

    public static final String MESSAGE_DETAIL_SUFFIX = "/messages/{id}";
    public static final String MESSAGE_DETAIL = BASE + MESSAGE_DETAIL_SUFFIX;

    public static final String MESSAGE_DELIVERIES_SUFFIX = "/messages/{id}/deliveries";
    public static final String MESSAGE_DELIVERIES = BASE + MESSAGE_DELIVERIES_SUFFIX;

    public static final String MESSAGE_RETRY_SUFFIX = "/messages/{id}/retry";
    public static final String MESSAGE_RETRY = BASE + MESSAGE_RETRY_SUFFIX;

    public static final String MESSAGE_CANCEL_SUFFIX = "/messages/{id}/cancel";
    public static final String MESSAGE_CANCEL = BASE + MESSAGE_CANCEL_SUFFIX;

    public static final String DEAD_LETTERS_SUFFIX = "/dead-letters";
    public static final String DEAD_LETTERS = BASE + DEAD_LETTERS_SUFFIX;

    public static final String DEAD_LETTER_DETAIL_SUFFIX = "/dead-letters/{id}";
    public static final String DEAD_LETTER_DETAIL = BASE + DEAD_LETTER_DETAIL_SUFFIX;

    public static final String DEAD_LETTER_REPLAY_SUFFIX = "/dead-letters/{id}/replay";
    public static final String DEAD_LETTER_REPLAY = BASE + DEAD_LETTER_REPLAY_SUFFIX;

    public static final String DEAD_LETTER_DISCARD_SUFFIX = "/dead-letters/{id}/discard";
    public static final String DEAD_LETTER_DISCARD = BASE + DEAD_LETTER_DISCARD_SUFFIX;

    public static final String DEAD_LETTER_COMPENSATE_SUFFIX = "/dead-letters/{id}/compensate";
    public static final String DEAD_LETTER_COMPENSATE = BASE + DEAD_LETTER_COMPENSATE_SUFFIX;

    public static final String STATS_SUFFIX = "/stats";
    public static final String STATS = BASE + STATS_SUFFIX;

    public static final String CONSUMERS_SUFFIX = "/consumers";
    public static final String CONSUMERS = BASE + CONSUMERS_SUFFIX;

    public static final String DISPATCH_SUFFIX = "/dispatch";
    public static final String DISPATCH = BASE + DISPATCH_SUFFIX;

    private MqApiPaths() {
    }
}