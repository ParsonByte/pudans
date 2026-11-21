package cn.tolim.pudans.core.live.resolve;

import cn.tolim.pudans.contract.live.LivePlatform;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LiveRoomLinkParser {

    private static final String HOST = "live.douyin.com";

    private static final Pattern ROOM_ID = Pattern.compile("[0-9]{1,20}");

    // 只接受抖音 HTTPS 房间链接并提取房间号
    public ParsedLiveRoom parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw LiveErrorCode.LIVE_ROOM_LINK_INVALID.exception();
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException error) {
            throw LiveErrorCode.LIVE_ROOM_LINK_INVALID.exception();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !HOST.equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw LiveErrorCode.LIVE_ROOM_LINK_INVALID.exception();
        }
        var path = Optional.ofNullable(uri.getPath()).orElse("");
        var roomId = path.replaceAll("^/+|/+$", "");
        if (!ROOM_ID.matcher(roomId).matches()) {
            throw LiveErrorCode.LIVE_ROOM_LINK_INVALID.exception();
        }
        return new ParsedLiveRoom(LivePlatform.DOUYIN, roomId, "https://" + HOST + "/" + roomId);
    }
}
