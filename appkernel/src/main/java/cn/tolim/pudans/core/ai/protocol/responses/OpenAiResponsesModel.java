package cn.tolim.pudans.core.ai.protocol.responses;

import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import cn.tolim.pudans.core.ai.protocol.AiProtocolProfiles;
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
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

public class OpenAiResponsesModel extends ChatModelBase {

    private static final ResponsesProfile STANDARD = new StandardResponsesProfile();

    private final AiChatProtocolRequest request;

    private final HttpTransport transport;

    private final ResponsesProfile profile;

    private final OpenAiResponsesCodec codec;

    public OpenAiResponsesModel(AiChatProtocolRequest request, AiProtocolProfile profile) {
        this(request, profile, HttpTransportFactory.getDefault());
    }

    OpenAiResponsesModel(
            AiChatProtocolRequest request, AiProtocolProfile profile, HttpTransport transport) {
        this.request = request;
        this.profile = AiProtocolProfiles.resolve(profile, ResponsesProfile.class, STANDARD);
        this.codec = new OpenAiResponsesCodec(this.profile);
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
        return profile.headers(request.apiKey(), request.headers());
    }
}
