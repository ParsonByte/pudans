package cn.tolim.pudans.core.live.resolve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.infra.exception.ApiException;
import org.junit.jupiter.api.Test;

class LiveRoomLinkParserTest {

    private final LiveRoomLinkParser parser = new LiveRoomLinkParser();

    @Test
    void parsesAValidDouyinLiveLink() {
        var parsed = parser.parse("https://live.douyin.com/106288756897");

        assertThat(parsed.roomId()).isEqualTo("106288756897");
        assertThat(parsed.roomUrl()).isEqualTo("https://live.douyin.com/106288756897");
    }

    @Test
    void rejectsNonHttpsLinks() {
        assertInvalid("http://live.douyin.com/106288756897");
    }

    @Test
    void rejectsOtherHosts() {
        assertInvalid("https://example.com/106288756897");
    }

    @Test
    void rejectsLinksWithUserInfo() {
        assertInvalid("https://user:" + "pass" + "@live.douyin.com/106288756897");
    }

    @Test
    void rejectsNonNumericRoomIds() {
        assertInvalid("https://live.douyin.com/not-a-room");
    }

    @Test
    void rejectsUnsupportedPorts() {
        assertInvalid("https://live.douyin.com:8443/106288756897");
    }

    private void assertInvalid(String link) {
        assertThatThrownBy(() -> parser.parse(link))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(LiveErrorCode.LIVE_ROOM_LINK_INVALID.name()));
    }
}
