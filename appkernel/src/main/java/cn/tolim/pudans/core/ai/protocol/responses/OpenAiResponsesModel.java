package cn.tolim.pudans.core.ai.protocol.responses;

import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ModelException;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.model.transport.HttpRequest;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.model.transport.HttpTransportFactory;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

public class OpenAiResponsesModel extends ChatModelBase {

    private final AiChatProtocolRequest request;

    private final HttpTransport transport;

    private final OpenAiResponsesCodec codec = new OpenAiResponsesCodec();

    public OpenAiResponsesModel(AiChatProtocolRequest request) {
        this(request, HttpTransportFactory.getDefault());
    }

    OpenAiResponsesModel(AiChatProtocolRequest request, HttpTransport transport) {
        this.request = request;
        this.transport = transport;
        if (request.contextWindow() != null && request.contextWindow() > 0) {
            setContextWindowSize(request.contextWindow());
        }
        setNativeStructuredOutput(request.nativeStructuredOutput());
        setNativeStructuredOutputWithTools(request.nativeStructuredOutputWithTools());
    }

    @Override
    public String getModelName() {
        return request.modelId();
    }

    @Override
    protected Flux<ChatResponse> doStream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        return Flux.defer(() -> {
            var body = codec.request(
                    request.providerId(),
                    request.modelId(),
                    messages,
                    tools,
                    options,
                    request.maxOutputTokens());
            var decoder = codec.streamDecoder(request.modelId(), request.providerId());
            var httpRequest = HttpRequest.builder()
                    .url(request.url())
                    .method("POST")
                    .headers(headers())
                    .body(JsonUtils.objectToJson(body))
                    .build();
            return transport
                    .stream(httpRequest)
                    .timeout(Duration.ofMillis(request.timeoutMs()))
                    .concatMapIterable(decoder::accept)
                    .onErrorMap(
                            error -> error instanceof ModelException
                                    ? error
                                    : new ModelException(
                                            "Responses API 请求失败。",
                                            error,
                                            request.modelId(),
                                            request.providerId()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, String> headers() {
        var headers = new LinkedHashMap<String, String>();
        headers.put("content-type", "application/json");
        headers.put("accept", "text/event-stream");
        headers.put("authorization", "Bearer " + request.apiKey());
        headers.putAll(request.headers());
        return Map.copyOf(headers);
    }
}
