package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

public final class AiProviderTestSupport {

    private AiProviderTestSupport() {}

    public static ClientResponse json(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    public static ClientResponse sse(String... data) {
        var body = new StringBuilder();
        for (var item : data) {
            for (var line : item.strip().split("\n")) {
                body.append("data: ").append(line).append('\n');
            }
            body.append('\n');
        }
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .body(body.toString())
                .build();
    }

    public static ClientResponse ndjson(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_NDJSON_VALUE)
                .body(body)
                .build();
    }

    public static String body(ClientRequest request) {
        var concrete = new MockClientHttpRequest(request.method(), request.url());
        request.writeTo(concrete, ExchangeStrategies.withDefaults()).block();
        return concrete.getBodyAsString().block();
    }

    static AiChatIr toolRequest(ObjectMapper mapper) {
        var arguments = mapper.createObjectNode().put("city", "Paris");
        var parameters = mapper.createObjectNode().put("type", "object");
        var toolResult = mapper.createObjectNode().put("temperature", 20);
        return new AiChatIr(
                List.of(
                        new AiChatMessage(
                                "system", List.of(new AiContentPart.Text("You are helpful.", null))),
                        new AiChatMessage("user", List.of(new AiContentPart.Text("hello", null))),
                        new AiChatMessage("assistant", List.of(
                                new AiContentPart.Text("checking", null),
                                new AiContentPart.ToolCall("call_1", "get_weather", arguments, null))),
                        new AiChatMessage(
                                "user", List.of(new AiContentPart.ToolResult("call_1", toolResult, null, null)))),
                List.of(new AiTool("get_weather", "Weather lookup", parameters, null)),
                new AiRequestParams("requested-model", 0.2, null, null, null, List.of(), null, Map.of()),
                false);
    }
}
