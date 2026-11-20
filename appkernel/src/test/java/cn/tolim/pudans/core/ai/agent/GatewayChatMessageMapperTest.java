package cn.tolim.pudans.core.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import java.util.List;
import org.junit.jupiter.api.Test;

class GatewayChatMessageMapperTest {

    @Test
    void roundTripsAudioAndVideoParts() {
        var message = new AiChatMessage(
                "user",
                List.of(
                        new AiContentPart.Audio("audio/mpeg", "YWJj", null, null, null),
                        new AiContentPart.Video("video/mp4", "YWJj", null, null, null)));

        var contents = GatewayChatMessageMapper.toContents(message);

        assertThat(contents).hasSize(2);
        assertThat(contents.get(0)).isInstanceOf(AudioContent.class);
        assertThat(contents.get(1)).isInstanceOf(VideoContent.class);

        var mapped = GatewayChatMessageMapper.toAiChatMessage(
                UserMessage.from(contents.toArray(Content[]::new)));

        assertThat(mapped.parts()).hasSize(2);
        assertThat(mapped.parts().get(0)).isInstanceOf(AiContentPart.Audio.class);
        assertThat(mapped.parts().get(1)).isInstanceOf(AiContentPart.Video.class);
    }
}