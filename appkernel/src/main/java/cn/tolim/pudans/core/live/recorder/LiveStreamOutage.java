// 断流告警计时状态机：从拉流重试循环中抽出，便于单测断流阈值与恢复时长

package cn.tolim.pudans.core.live.recorder;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

// 断流计时：记录首次失败时刻，达到阈值只告警一次，恢复后返回本次摘要
public class LiveStreamOutage {

    // 首次失败时刻，用来计算本次断流持续多久
    private Instant startedAt;

    // 同一段断流只告警一次，重连期间不重复打扰
    private boolean alerted;

    // 最近一次失败原因，恢复告警沿用
    @Nullable
    private String errorCode;

    @Nullable
    private String message;

    // 记录断流起点与最近失败原因，重复调用保留最早时刻
    public void begin(Instant at, @Nullable String errorCode, @Nullable String message) {
        if (startedAt == null) {
            startedAt = at;
        }
        this.errorCode = errorCode;
        this.message = message;
    }

    // 本次断流起点，未断流时为空
    @Nullable
    public Instant startedAt() {
        return startedAt;
    }

    // 断流时长达到阈值且尚未告警时返回真
    public boolean alertDue(Instant now, Duration threshold) {
        return startedAt != null && !alerted && !now.isBefore(startedAt.plus(threshold));
    }

    // 标记本次断流已告警，后续失败不再重复
    public void markAlerted() {
        alerted = true;
    }

    // 恢复后返回本次断流摘要并归零，未断流时返回空
    @Nullable
    public Outage end(Instant now) {
        if (startedAt == null) {
            return null;
        }
        var outage = new Outage(startedAt, Duration.between(startedAt, now), alerted, errorCode, message);
        startedAt = null;
        alerted = false;
        errorCode = null;
        message = null;
        return outage;
    }

    // 本次断流摘要：起点、时长、是否已发中断告警、最近失败原因
    public record Outage(
            Instant startedAt,
            Duration duration,
            boolean alerted,
            @Nullable String errorCode,
            @Nullable String message) {}
}
