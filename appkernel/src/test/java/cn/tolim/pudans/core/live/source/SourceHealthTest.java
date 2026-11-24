package cn.tolim.pudans.core.live.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.contract.live.LiveSourceState;
import cn.tolim.pudans.contract.live.dto.LiveRoomInfo;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceHealthTest {

    private final SourceHealth sourceHealth = new SourceHealth(mock(ReactiveExtensionClient.class));

    @Test
    void copiesLastStreamAtFromSourceStatus() {
        var streamAt = Instant.parse("2026-11-23T08:00:00Z");
        var source = new LiveSource();
        var metadata = new Metadata();
        metadata.setName("douyin-1");
        source.setMetadata(metadata);
        source.getStatus().setState(LiveSourceState.LIVE);
        source.getStatus().setLastStreamAt(streamAt);

        var health = sourceHealth.of(source);

        assertThat(health.lastStreamAt()).isEqualTo(streamAt);
    }

    @Test
    void previewHasNoLastStreamAt() {
        var resolvedAt = Instant.parse("2026-11-23T08:30:00Z");
        var info = new LiveRoomInfo(
                "123456789", "标题", "主播", null, null, true, "live", List.of(), resolvedAt);

        var health = sourceHealth.preview("douyin-1", info);

        assertThat(health.lastStreamAt()).isNull();
        assertThat(health.lastResolvedAt()).isEqualTo(resolvedAt);
    }
}
