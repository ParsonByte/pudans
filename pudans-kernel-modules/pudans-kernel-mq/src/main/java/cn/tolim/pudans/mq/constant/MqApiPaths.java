package cn.tolim.pudans.mq.constant;

// Message center API paths
public final class MqApiPaths {

    public static final String API_PREFIX = "/apis/pudans.tolim.cn/v1alpha1/mq";

    public static final String MESSAGES = API_PREFIX + "/messages";
    public static final String DEAD_LETTERS = API_PREFIX + "/dead-letters";
    public static final String STATS = API_PREFIX + "/stats";
    public static final String CONSUMERS = API_PREFIX + "/consumers";
    public static final String DISPATCH = API_PREFIX + "/dispatch";

    private MqApiPaths() {
    }
}