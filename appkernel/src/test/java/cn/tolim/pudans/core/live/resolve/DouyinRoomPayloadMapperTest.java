package cn.tolim.pudans.core.live.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import org.junit.jupiter.api.Test;

class DouyinRoomPayloadMapperTest {

    private final DouyinRoomPayloadMapper mapper = new DouyinRoomPayloadMapper(new LiveStreamUrlValidator());

    @Test
    void mapsRoomAndOrderedStreams() throws Exception {
        var json = """
                {
                  "state": {
                    "roomStore": {
                      "roomInfo": {
                        "room": {"status": 2, "title": "测试直播"},
                        "anchor": {
                          "nickname": "主播",
                          "sec_uid": "anchor-id",
                          "avatar_thumb": {"url_list": ["https://p3.douyinpic.com/avatar.jpg"]}
                        },
                        "web_stream_url": {
                          "hls_pull_url_map": {
                            "FULL_HD1": "https://pull.example.com/full.m3u8",
                            "SD1": "https://pull.example.com/sd1.m3u8"
                          },
                          "flv_pull_url": {
                            "FULL_HD1": "https://pull.example.com/full.flv"
                          }
                        }
                      }
                    }
                  }
                }
                """;

        var info = mapper.map(JsonUtils.mapper().readTree(json), "106288756897");

        assertThat(info.roomId()).isEqualTo("106288756897");
        assertThat(info.title()).isEqualTo("测试直播");
        assertThat(info.anchorName()).isEqualTo("主播");
        assertThat(info.live()).isTrue();
        assertThat(info.streams()).hasSize(3);
        assertThat(info.streams().get(0).url()).isEqualTo("https://pull.example.com/full.m3u8");
        assertThat(info.streams().get(1).url()).isEqualTo("https://pull.example.com/sd1.m3u8");
        assertThat(info.streams().get(2).url()).isEqualTo("https://pull.example.com/full.flv");
    }
}
