package cn.tolim.pudans.core.live.source;

import cn.tolim.pudans.contract.live.LiveSourceState;
import org.springframework.stereotype.Component;

// 直播源状态迁移是纯函数，事件到目标状态的映射集中在这里
@Component
public class LiveSourceStateMachine {

    public enum Event {
        ENABLED,
        DISABLED,
        RESOLVED_LIVE,
        RESOLVED_OFFLINE,
        RESOLVE_FAILED,
        RECORDING_STARTED,
        RECORDING_ENDED,
        RECORDING_FAILED
    }

    public record Transition(LiveSourceState from, LiveSourceState to, String reason) {

        // 判断迁移是否真正改变了状态
        public boolean changed() {
            return from != to;
        }
    }

    // 纯函数映射事件到目标状态，未知状态按空闲处理
    public Transition next(LiveSourceState current, Event event) {
        var from = current == null ? LiveSourceState.IDLE : current;
        return switch (event) {
            case ENABLED -> new Transition(
                    from,
                    from == LiveSourceState.IDLE || from == LiveSourceState.DISABLED
                            ? LiveSourceState.MONITORING
                            : from,
                    "enabled");
            case DISABLED -> new Transition(from, LiveSourceState.DISABLED, "disabled");
            case RESOLVED_LIVE -> new Transition(
                    from,
                    from == LiveSourceState.RECORDING ? LiveSourceState.RECORDING : LiveSourceState.LIVE,
                    "resolve_online");
            case RESOLVED_OFFLINE -> new Transition(from, LiveSourceState.OFFLINE, "resolve_offline");
            case RESOLVE_FAILED -> new Transition(from, LiveSourceState.ERROR, "resolve_failed");
            case RECORDING_STARTED -> new Transition(from, LiveSourceState.RECORDING, "recording_started");
            case RECORDING_ENDED -> new Transition(
                    from,
                    from == LiveSourceState.RECORDING || from == LiveSourceState.LIVE
                            ? LiveSourceState.LIVE
                            : from,
                    "recording_ended");
            case RECORDING_FAILED -> new Transition(
                    from,
                    from == LiveSourceState.DISABLED ? LiveSourceState.DISABLED : LiveSourceState.ERROR,
                    "recording_failed");
        };
    }
}
