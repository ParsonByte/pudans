package cn.tolim.pudans.core.ai.gateway.agent;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiChatRequest;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiDefaultAgentAdapterTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final OpenAiDefaultAgentAdapter adapter = new OpenAiDefaultAgentAdapter();

    @Test
    void joinsAllTextBlocksOfTheLastUserMessage() {
        var ir = ir(List.of(
                new AiChatMessage("user", List.of(new AiContentPart.Text("ignored", null))),
                new AiChatMessage("assistant", List.of(new AiContentPart.Text("answer", null))),
                new AiChatMessage("user", List.of(
                        new AiContentPart.Text("hello", null),
                        new AiContentPart.Custom("image_url", mapper.createObjectNode()),
                        new AiContentPart.Text("world", null)))));

        assertThat(adapter.extractUserText(request(ir))).contains("hello\nworld");
    }

    @Test
    void returnsEmptyWhenThereIsNoUserMessage() {
        var ir = ir(List.of(new AiChatMessage("system", List.of(new AiContentPart.Text("rules", null)))));
        assertThat(adapter.extractUserText(request(ir))).isEmpty();
    }

    @Test
    void classifiesEveryRequestAsMain() {
        var ir = ir(List.of(new AiChatMessage("user", List.of(new AiContentPart.Text("hello", null)))));
        assertThat(adapter.classifyRequest(request(ir))).isEqualTo(AiRequestKind.MAIN);
        assertThat(adapter.source()).isEqualTo("openai");
    }

    private static AiChatIr ir(List<AiChatMessage> messages) {
        var params = new AiRequestParams("gpt-4o-mini", null, 256, null, null, List.of(), null, Map.of());
        return new AiChatIr(messages, List.of(), params, false);
    }

    private static AiChatRequest request(AiChatIr ir) {
        return new AiChatRequest(AiProvider.Protocol.OPENAI_CHAT, ir);
    }
}
