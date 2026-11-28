package cn.tolim.pudans.notification.controller;

// REST paths exposed by the notification module.
public final class NotificationApiPaths {

    public static final String BASE = "/apis/pudans.tolim.cn/v1alpha1/notification";
    public static final String TEMPLATES = BASE + "/templates";
    public static final String PROVIDERS = BASE + "/providers";
    public static final String SEND = BASE + "/send";
    public static final String RECORDS = BASE + "/records";
    public static final String DEAD_LETTERS = BASE + "/dead-letters";
    public static final String STATS = BASE + "/stats";

    private NotificationApiPaths() {
    }
}