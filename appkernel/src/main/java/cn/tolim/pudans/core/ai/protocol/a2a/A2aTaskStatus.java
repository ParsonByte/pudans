package cn.tolim.pudans.core.ai.protocol.a2a;

import org.springframework.util.StringUtils;

public record A2aTaskStatus(String state) {

    public static final String SUBMITTED = "submitted";

    public static final String WORKING = "working";

    public static final String COMPLETED = "completed";

    public static final String FAILED = "failed";

    public static final String CANCELED = "canceled";

    public A2aTaskStatus {
        if (!StringUtils.hasText(state)) {
            throw new IllegalArgumentException("任务状态不能为空。");
        }
    }

    public boolean terminal() {
        return COMPLETED.equals(state) || FAILED.equals(state) || CANCELED.equals(state);
    }
}