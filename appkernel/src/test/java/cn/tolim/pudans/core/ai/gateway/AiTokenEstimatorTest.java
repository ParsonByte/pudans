package cn.tolim.pudans.core.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiTokenEstimatorTest {

    @Test
    void countsCjkCharactersAsSingleTokens() {
        assertThat(AiTokenEstimator.estimateText("你好世界")).isEqualTo(4);
    }

    @Test
    void groupsLatinCharactersByFour() {
        assertThat(AiTokenEstimator.estimateText("abcdefgh")).isEqualTo(2);
        assertThat(AiTokenEstimator.estimateText("abcde")).isEqualTo(2);
    }

    @Test
    void addsPerMessageOverhead() {
        var messages = List.of(
                new AiChatMessage("system", List.of(new AiContentPart.Text("规则", null))),
                new AiChatMessage("user", List.of(new AiContentPart.Text("abcde", null))));

        assertThat(AiTokenEstimator.estimateMessages(messages)).isEqualTo(4 + 2 + 4 + 2);
    }

    @Test
    void countsImagesAndToolCalls() {
        var messages = List.of(new AiChatMessage("user", List.of(
                new AiContentPart.Image("image/png", null, "https://example.test/a.png", null, null),
                new AiContentPart.ToolCall("call-1", "get_weather", null, null))));

        assertThat(AiTokenEstimator.estimateMessages(messages)).isEqualTo(4 + 85 + 3);
    }

    @Test
    void emptyMessagesCostNothing() {
        assertThat(AiTokenEstimator.estimateMessages(List.of())).isZero();
    }
}
