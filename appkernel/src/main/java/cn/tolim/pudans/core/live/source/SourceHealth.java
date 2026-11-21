package cn.tolim.pudans.core.live.source;

import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.contract.live.LiveSourceState;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.contract.live.dto.LiveRoomInfo;
import cn.tolim.pudans.contract.live.dto.LiveSourceHealth;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

// 单直播源健康快照，同时用于解析预览结果
@Component
@RequiredArgsConstructor
public class SourceHealth {

    private final ReactiveExtensionClient client;

    public Mono<LiveSourceHealth> health(String name) {
        return client.fetch(LiveSource.class, name)
                .switchIfEmpty(Mono.error(LiveErrorCode.LIVE_SOURCE_NOT_FOUND.exception()))
                .map(this::of);
    }

    public LiveSourceHealth of(LiveSource source) {
        var status = source.getStatus();
        return new LiveSourceHealth(
                source.getMetadata().getName(),
                status.getState() == null ? null : status.getState().name(),
                status.isLive(),
                status.getRoomTitle(),
                status.getAnchorName(),
                status.getLastResolvedAt(),
                status.getNextCheckAt(),
                status.getCooldownUntil(),
                status.getCurrentRecording(),
                status.getLastResolveCode(),
                status.getLastResolveError());
    }

    public LiveSourceHealth preview(String name, LiveRoomInfo info) {
        var now = Instant.now();
        return new LiveSourceHealth(
                name,
                info.live() ? LiveSourceState.LIVE.name() : LiveSourceState.OFFLINE.name(),
                info.live(),
                info.title(),
                info.anchorName(),
                info.resolvedAt() == null ? now : info.resolvedAt(),
                null,
                null,
                null,
                null,
                null);
    }
}
