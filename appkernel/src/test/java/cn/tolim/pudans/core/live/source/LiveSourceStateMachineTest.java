package cn.tolim.pudans.core.live.source;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.live.resource.LiveSourceState;
import org.junit.jupiter.api.Test;

class LiveSourceStateMachineTest {

    private final LiveSourceStateMachine stateMachine = new LiveSourceStateMachine();

    @Test
    void enablesIdleSourceToMonitoring() {
        var transition = stateMachine.next(LiveSourceState.IDLE, LiveSourceStateMachine.Event.ENABLED);

        assertThat(transition.to()).isEqualTo(LiveSourceState.MONITORING);
        assertThat(transition.changed()).isTrue();
    }

    @Test
    void enablesDisabledSourceBackToMonitoring() {
        var transition =
                stateMachine.next(LiveSourceState.DISABLED, LiveSourceStateMachine.Event.ENABLED);

        assertThat(transition.to()).isEqualTo(LiveSourceState.MONITORING);
        assertThat(transition.reason()).isEqualTo("enabled");
    }

    @Test
    void disablesAnyState() {
        for (var state : LiveSourceState.values()) {
            var transition = stateMachine.next(state, LiveSourceStateMachine.Event.DISABLED);

            assertThat(transition.to()).isEqualTo(LiveSourceState.DISABLED);
            assertThat(transition.changed()).isEqualTo(state != LiveSourceState.DISABLED);
        }
    }

    @Test
    void resolvesOnlineToLiveWithoutRecording() {
        var transition =
                stateMachine.next(LiveSourceState.MONITORING, LiveSourceStateMachine.Event.RESOLVED_LIVE);

        assertThat(transition.to()).isEqualTo(LiveSourceState.LIVE);
    }

    @Test
    void keepsRecordingWhenResolveSeesOnline() {
        var transition =
                stateMachine.next(LiveSourceState.RECORDING, LiveSourceStateMachine.Event.RESOLVED_LIVE);

        assertThat(transition.to()).isEqualTo(LiveSourceState.RECORDING);
        assertThat(transition.changed()).isFalse();
    }

    @Test
    void resolvesOfflineToOffline() {
        var transition = stateMachine.next(
                LiveSourceState.MONITORING, LiveSourceStateMachine.Event.RESOLVED_OFFLINE);

        assertThat(transition.to()).isEqualTo(LiveSourceState.OFFLINE);
    }

    @Test
    void repeatedResolveFailuresKeepError() {
        var first = stateMachine.next(LiveSourceState.MONITORING, LiveSourceStateMachine.Event.RESOLVE_FAILED);
        var again = stateMachine.next(first.to(), LiveSourceStateMachine.Event.RESOLVE_FAILED);

        assertThat(first.to()).isEqualTo(LiveSourceState.ERROR);
        assertThat(again.to()).isEqualTo(LiveSourceState.ERROR);
        assertThat(again.changed()).isFalse();
    }

    @Test
    void startsAndEndsRecording() {
        var started =
                stateMachine.next(LiveSourceState.LIVE, LiveSourceStateMachine.Event.RECORDING_STARTED);
        var ended = stateMachine.next(started.to(), LiveSourceStateMachine.Event.RECORDING_ENDED);

        assertThat(started.to()).isEqualTo(LiveSourceState.RECORDING);
        assertThat(ended.to()).isEqualTo(LiveSourceState.LIVE);
    }

    @Test
    void recordingFailureEntersErrorExceptWhenDisabled() {
        var failed =
                stateMachine.next(LiveSourceState.RECORDING, LiveSourceStateMachine.Event.RECORDING_FAILED);
        var disabled = stateMachine.next(
                LiveSourceState.DISABLED, LiveSourceStateMachine.Event.RECORDING_FAILED);

        assertThat(failed.to()).isEqualTo(LiveSourceState.ERROR);
        assertThat(disabled.to()).isEqualTo(LiveSourceState.DISABLED);
    }

    @Test
    void treatsMissingStateAsIdle() {
        var transition = stateMachine.next(null, LiveSourceStateMachine.Event.ENABLED);

        assertThat(transition.from()).isEqualTo(LiveSourceState.IDLE);
        assertThat(transition.to()).isEqualTo(LiveSourceState.MONITORING);
    }
}
