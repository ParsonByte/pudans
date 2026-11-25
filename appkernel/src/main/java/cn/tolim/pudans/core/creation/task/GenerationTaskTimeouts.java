package cn.tolim.pudans.core.creation.task;

import java.time.Duration;

final class GenerationTaskTimeouts {

    static final Duration PROVIDER_STATUS = Duration.ofSeconds(30);

    static final Duration PROVIDER_CANCEL = Duration.ofSeconds(30);

    private GenerationTaskTimeouts() {}
}
