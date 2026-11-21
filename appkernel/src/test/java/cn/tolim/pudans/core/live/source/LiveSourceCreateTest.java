package cn.tolim.pudans.core.live.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.live.resource.LivePlatform;
import cn.tolim.pudans.contract.live.resource.LiveSource;
import cn.tolim.pudans.contract.live.resource.LiveSourceState;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.contract.live.dto.LiveRoomInfo;
import cn.tolim.pudans.core.live.recorder.LiveStoragePolicyResolver;
import cn.tolim.pudans.core.live.recording.Recordings;
import cn.tolim.pudans.core.live.resolve.RoomResolver;
import cn.tolim.pudans.core.live.resolve.LiveRoomLinkParser;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.LiveProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class LiveSourceCreateTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    RoomResolver roomResolver;

    @Mock
    SourceStatus sourceStatus;

    @Mock
    LiveSourceRegistry registry;

    @Mock
    SourceHealth sourceHealth;

    @Mock
    Recordings recordings;

    private Sources service() {
        var validator = new LiveSourceValidator(
                new LiveRoomLinkParser(), client, mock(LiveStoragePolicyResolver.class));
        return new Sources(
                client,
                validator,
                roomResolver,
                sourceStatus,
                registry,
                sourceHealth,
                recordings,
                new LiveProperties());
    }

    private static LiveRoomInfo offlineInfo() {
        return new LiveRoomInfo(
                "123456789", "标题", "主播", "头像", "anchor-1", false, "offline", List.of(), Instant.now());
    }

    @Test
    void createsOfflineSourceWithDerivedNameAndDefaults() {
        when(client.fetch(LiveSource.class, "douyin-123456789")).thenReturn(Mono.empty());
        when(roomResolver.resolve(LivePlatform.DOUYIN, "123456789", false))
                .thenReturn(Mono.just(offlineInfo()));
        when(client.create(any(LiveSource.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service().create("https://live.douyin.com/123456789", null, false, null, null, null))
                .assertNext(source -> {
                    assertThat(source.getMetadata().getName()).isEqualTo("douyin-123456789");
                    assertThat(source.getSpec().isAutoRecord()).isFalse();
                    assertThat(source.getSpec().getSegmentSeconds()).isEqualTo(30);
                    assertThat(source.getSpec().getMergeIntervalSeconds()).isEqualTo(3600);
                    assertThat(source.getStatus().getState()).isEqualTo(LiveSourceState.OFFLINE);
                    assertThat(source.getStatus().getNextCheckAt()).isNotNull();
                    assertThat(source.getStatus().getMessage())
                            .isEqualTo(LiveErrorCode.LIVE_ROOM_OFFLINE.message());
                })
                .verifyComplete();
    }

    @Test
    void rejectsDuplicateSourceBeforeResolving() {
        var existing = new LiveSource();
        var metadata = new Metadata();
        metadata.setName("douyin-123456789");
        existing.setMetadata(metadata);
        when(client.fetch(LiveSource.class, "douyin-123456789")).thenReturn(Mono.just(existing));

        StepVerifier.create(service().create("https://live.douyin.com/123456789", null, false, null, null, null))
                .expectErrorMatches(error -> error instanceof ApiException api
                        && LiveErrorCode.LIVE_SOURCE_DUPLICATE.name().equals(api.getCode()))
                .verify();
        verifyNoInteractions(roomResolver);
    }

    @Test
    void rejectsInvalidSegmentSeconds() {
        StepVerifier.create(service().create("https://live.douyin.com/123456789", null, false, 15, null, null))
                .expectErrorMatches(error -> error instanceof ApiException api
                        && LiveErrorCode.LIVE_INVALID_SPEC.name().equals(api.getCode()))
                .verify();
        verifyNoInteractions(roomResolver);
    }}
