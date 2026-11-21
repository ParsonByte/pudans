package cn.tolim.pudans.core.live.resolve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.infra.exception.ApiException;
import org.junit.jupiter.api.Test;

class PacePayloadExtractorTest {

    private final PacePayloadExtractor extractor = new PacePayloadExtractor();

    @Test
    void extractsTheLastPacePayload() throws Exception {
        var payload = "{\"state\":{\"roomStore\":{\"roomInfo\":{\"room\":{\"status\":2}}}}}";
        var encoded = "c:" + payload;
        var html = "<script>self.__pace_f.push([1," + JsonUtils.mapper().writeValueAsString(encoded) + "])</script>";

        var node = extractor.extract(html);

        assertThat(node.path("state").path("roomStore").path("roomInfo").path("room").path("status").asInt())
                .isEqualTo(2);
    }

    @Test
    void rejectsHtmlWithoutPayload() {
        assertThatThrownBy(() -> extractor.extract("<html></html>"))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(LiveErrorCode.LIVE_RESOLVE_FAILED.name()));
    }
}
