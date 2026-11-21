package cn.tolim.pudans.core.live.resolve;

import cn.tolim.pudans.contract.live.LivePlatform;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.contract.live.dto.LiveRoomInfo;
import cn.tolim.pudans.contract.live.dto.LiveStreamProtocol;
import cn.tolim.pudans.contract.live.dto.LiveStreamUrl;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class DouyinRoomPayloadMapper {

    private static final String[] QUALITIES = {"FULL_HD1", "SD1", "SD2"};

    private final LiveStreamUrlValidator validator;

    public LiveRoomInfo map(JsonNode payload, String expectedRoomId) {
        var roomInfo = payload.path("state").path("roomStore").path("roomInfo");
        if (!roomInfo.isObject()) {
            throw LiveErrorCode.LIVE_RESOLVE_FAILED.exception();
        }
        var room = roomInfo.path("room");
        var title = text(room, "title");
        var liveStatus = text(room, "status");
        var anchor = roomInfo.path("anchor");
        var anchorName = text(anchor, "nickname");
        var anchorAvatar = text(anchor.path("avatar_thumb").path("url_list"), 0);
        var anchorId = text(anchor, "sec_uid");
        var streams = streams(roomInfo.path("web_stream_url"));
        var live = room.path("status").asInt(-1) == 2 || (liveStatus == null && !streams.isEmpty());
        return new LiveRoomInfo(
                expectedRoomId,
                title,
                anchorName,
                anchorAvatar,
                anchorId,
                live,
                liveStatus,
                streams,
                Instant.now());
    }

    private List<LiveStreamUrl> streams(JsonNode webStreamUrl) {
        var streams = new ArrayList<LiveStreamUrl>();
        var seen = new LinkedHashMap<String, Boolean>();
        addQuality(streams, seen, webStreamUrl.path("hls_pull_url_map"), LiveStreamProtocol.HLS);
        addQuality(streams, seen, webStreamUrl.path("flv_pull_url"), LiveStreamProtocol.FLV);
        return List.copyOf(streams);
    }

    private void addQuality(
            List<LiveStreamUrl> streams,
            LinkedHashMap<String, Boolean> seen,
            JsonNode urls,
            LiveStreamProtocol protocol) {
        for (var quality : QUALITIES) {
            var raw = text(urls, quality);
            if (raw == null || seen.putIfAbsent(raw, Boolean.TRUE) != null) {
                continue;
            }
            validator.sanitize(raw).ifPresent(url -> streams.add(new LiveStreamUrl(protocol, quality, url)));
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        var text = value.asText(null);
        return StringUtils.hasText(text) ? text : null;
    }

    private static String text(JsonNode node, int index) {
        if (node == null || !node.isArray() || node.size() <= index) {
            return null;
        }
        var text = node.get(index).asText(null);
        return StringUtils.hasText(text) ? text : null;
    }
}
