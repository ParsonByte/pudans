package cn.tolim.pudans.core.live.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.live.LivePlatform;
import cn.tolim.pudans.contract.core.extension.live.LiveSource;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.live.LiveErrorCode;
import cn.tolim.pudans.core.live.recorder.LiveStoragePolicyResolver;
import cn.tolim.pudans.core.live.resolve.LiveRoomLinkParser;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class LiveSourceValidatorTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    LiveStoragePolicyResolver storagePolicyResolver;

    private LiveSourceValidator validator() {
        return new LiveSourceValidator(new LiveRoomLinkParser(), client, storagePolicyResolver);
    }

    @Test
    void parsesValidRoomUrlAndDerivesName() {
        var room = validator().parseRoomUrl("https://live.douyin.com/123456789");

        assertThat(room.platform()).isEqualTo(LivePlatform.DOUYIN);
        assertThat(room.roomId()).isEqualTo("123456789");
        assertThat(LiveSourceValidator.resourceName(room)).isEqualTo("douyin-123456789");
    }

    @Test
    void rejectsBlankRoomUrl() {
        assertThatThrownBy(() -> validator().parseRoomUrl("  "))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo(LiveErrorCode.LIVE_ROOM_LINK_INVALID.name());
    }

    @Test
    void rejectsLinkOfAnotherHost() {
        assertThatThrownBy(() -> validator().parseRoomUrl("https://example.com/123"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo(LiveErrorCode.LIVE_ROOM_LINK_INVALID.name());
    }

    @Test
    void validatesSegmentAndMergeArguments() {
        assertThat(LiveSourceValidator.segmentSeconds(null)).isEqualTo(30);
        assertThat(LiveSourceValidator.segmentSeconds(10)).isEqualTo(10);
        assertThat(LiveSourceValidator.segmentSeconds(30)).isEqualTo(30);
        assertThat(LiveSourceValidator.mergeIntervalSeconds(null)).isEqualTo(3600);
        assertThat(LiveSourceValidator.mergeIntervalSeconds(0)).isZero();
        assertThat(LiveSourceValidator.mergeIntervalSeconds(60)).isEqualTo(60);

        assertThatThrownBy(() -> LiveSourceValidator.segmentSeconds(15))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> LiveSourceValidator.mergeIntervalSeconds(30))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsDuplicateSourceWithSamePlatformAndRoomId() {
        var existing = new LiveSource();
        var metadata = new Metadata();
        metadata.setName("douyin-123456789");
        existing.setMetadata(metadata);
        when(client.fetch(LiveSource.class, "douyin-123456789")).thenReturn(Mono.just(existing));

        StepVerifier.create(validator().ensureAbsent("douyin-123456789"))
                .expectErrorMatches(error -> error instanceof ApiException api
                        && LiveErrorCode.LIVE_SOURCE_DUPLICATE.name().equals(api.getCode()))
                .verify();
    }

    @Test
    void acceptsAbsentSourceName() {
        when(client.fetch(LiveSource.class, "douyin-123456789")).thenReturn(Mono.empty());

        StepVerifier.create(validator().ensureAbsent("douyin-123456789")).verifyComplete();
    }

    @Test
    void skipsStoragePolicyWhenAutoRecordDisabledAndPolicyMissing() {
        StepVerifier.create(validator().resolveStoragePolicy(false, null))
                .assertNext(policy -> assertThat(policy).isEqualTo(Optional.empty()))
                .verifyComplete();
    }
}
