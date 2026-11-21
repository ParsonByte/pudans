package cn.tolim.pudans.core.live.source;

import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.core.live.recorder.LiveStoragePolicyResolver;
import cn.tolim.pudans.core.live.resolve.LiveRoomLinkParser;
import cn.tolim.pudans.core.live.resolve.ParsedLiveRoom;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

// 创建直播源前的链接、参数、重复和存储策略校验
@Component
@RequiredArgsConstructor
public class LiveSourceValidator {

    public static final int DEFAULT_SEGMENT_SECONDS = 30;

    public static final int DEFAULT_MERGE_INTERVAL_SECONDS = 3600;

    private static final int MIN_SEGMENT_SECONDS = 10;

    private static final int MAX_SEGMENT_SECONDS = 30;

    private static final int MAX_MERGE_INTERVAL_SECONDS = 21600;

    private final LiveRoomLinkParser linkParser;

    private final ReactiveExtensionClient client;

    private final LiveStoragePolicyResolver storagePolicyResolver;

    // 校验非空后交给链接解析器提取房间信息
    public ParsedLiveRoom parseRoomUrl(String roomUrl) {
        if (!StringUtils.hasText(roomUrl)) {
            throw LiveErrorCode.LIVE_ROOM_LINK_INVALID.exception();
        }
        return linkParser.parse(roomUrl);
    }

    // 直播源资源名由平台和房间号唯一确定
    public static String resourceName(ParsedLiveRoom room) {
        return room.platform().name().toLowerCase(Locale.ROOT) + "-" + room.roomId();
    }

    // 只允许默认值或上下限，其他值拒绝
    public static int segmentSeconds(Integer value) {
        if (value == null) {
            return DEFAULT_SEGMENT_SECONDS;
        }
        if (value != MIN_SEGMENT_SECONDS && value != MAX_SEGMENT_SECONDS) {
            throw LiveErrorCode.LIVE_INVALID_SPEC.exception();
        }
        return value;
    }

    // 允许零表示按需合并，否则限制在区间内
    public static int mergeIntervalSeconds(Integer value) {
        if (value == null) {
            return DEFAULT_MERGE_INTERVAL_SECONDS;
        }
        if (value != 0 && (value < 60 || value > MAX_MERGE_INTERVAL_SECONDS)) {
            throw LiveErrorCode.LIVE_INVALID_SPEC.exception();
        }
        return value;
    }

    // 同名资源已存在时拒绝创建
    public Mono<Void> ensureAbsent(String name) {
        return client.fetch(LiveSource.class, name)
                .flatMap(existing -> Mono.<Void>error(LiveErrorCode.LIVE_SOURCE_DUPLICATE.exception()))
                .then();
    }

    // 自动录制或显式指定策略时必须解析为已就绪的对象存储
    public Mono<Optional<String>> resolveStoragePolicy(Boolean autoRecord, String storagePolicy) {
        var required = autoRecord == null || autoRecord;
        if (!StringUtils.hasText(storagePolicy)) {
            if (!required) {
                return Mono.just(Optional.empty());
            }
            return storagePolicyResolver
                    .resolve(null)
                    .map(policy -> Optional.of(policy.getMetadata().getName()));
        }
        return storagePolicyResolver
                .resolve(storagePolicy)
                .map(policy -> Optional.of(policy.getMetadata().getName()));
    }
}
