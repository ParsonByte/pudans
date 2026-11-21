package cn.tolim.pudans.core.live.recorder;

import cn.tolim.pudans.infra.properties.LiveProperties;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 断流重连退避：1s 起按 2 倍增长，封顶后叠加抖动
@Component
@RequiredArgsConstructor
public class LiveRecorderBackoff {

    private final LiveProperties properties;

    public Duration next(int attempt) {
        var recorder = properties.getRecorder();
        var shift = Math.min(Math.max(attempt - 1, 0), 10);
        var backoff = Math.min(recorder.getRetryBackoff().toMillis() << shift, recorder.getMaxRetryBackoff().toMillis());
        var jitter = recorder.getRetryJitter();
        if (jitter > 0) {
            var factor = 1 + ThreadLocalRandom.current().nextDouble(-jitter, jitter);
            backoff = Math.max(1, Math.round(backoff * factor));
        }
        return Duration.ofMillis(backoff);
    }
}
