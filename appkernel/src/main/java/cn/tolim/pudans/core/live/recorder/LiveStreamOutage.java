// 断流告警计时状态机：从拉流重试循环中抽出，便于单测断流阈值与恢复时长

package cn.tolim.pudans.core.live.recorder;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

// 断流计时：记录首次失败时刻，达到阈值只告警一次，恢复后归零
public class LiveStreamOutage {

    // 首次失败时刻，用来计算本次断流持续多久
    private Instant startedAt;

    // 同一段断流只告警一次，重连期间不重复打扰
    private boolean alerted;

    // 记录断流起点，重复调用保留最早时刻
    public void begin(Instant at) {
        if (startedAt == null) {
            startedAt = at;
        }
    }

    // 断流时长达到阈值且尚未告警时返回真
    public boolean alertDue(Instant now, Duration threshold) {
        return startedAt != null && !alerted && !now.isBefore(startedAt.plus(threshold));
    }

    // 标记本次断流已告警，后续失败不再重复
    public void markAlerted() {
        alerted = true;
    }

    // 恢复后返回本次断流时长并归零，未断流时返回空
    @Nullable
    public Duration end(Instant now) {
        if (startedAt == null) {
            return null;
        }
        var duration = Duration.between(startedAt, now);
        startedAt = null;
        alerted = false;
        return duration;
    }
}
