package cn.tolim.pudans.core.ai.protocol.agui;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.harness.agent.AgentRun;
import cn.tolim.pudans.core.ai.protocol.ProtocolAgentRuntime;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AguiEndpoint implements CustomEndpoint {

    private static final String TAG = "AiAguiV1alpha1";

    private static final String HEADER_AGENT = "X-Agent";

    private static final String CODE_RUN_FAILED = "ai-agui-run-failed";

    private static final String CODE_ENCODE_FAILED = "ai-agui-encode-failed";

    private final ProtocolAgentRuntime runtime;

    private final AiEndpointRequests requests;

    private final ObjectMapper mapper;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/aiagui/-/run", this::run, builder -> builder
                        .operationId("runAiAguiAgent")
                        .description("Run an Agent with the AG-UI protocol and stream events as server-sent events.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true))
                        .response(responseBuilder().description("AG-UI event stream.")))
                .build();
    }

    private Mono<ServerResponse> run(ServerRequest request) {
        var headerAgent = request.headers().firstHeader(HEADER_AGENT);
        return requests
                .userId(request)
                .zipWith(request.bodyToMono(String.class).defaultIfEmpty(""))
                .flatMap(tuple -> {
                    var input = AguiRunRequest.parse(mapper, tuple.getT2(), headerAgent);
                    var runRequest = new ProtocolAgentRuntime.ProtocolRunRequest(
                            tuple.getT1(),
                            input.agentName(),
                            input.threadId(),
                            null,
                            UserMessage.from(input.input()),
                            true,
                            Map.of());
                    return runtime.start(runRequest).flatMap(run -> stream(run, input));
                });
    }

    private Mono<ServerResponse> stream(AgentRun run, AguiRunRequest input) {
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(events(run, input), String.class);
    }

    // 复用 AgentRun 的事件监听与一次性结果订阅；事件 sink 不完成，不能直接作为响应体。
    private Flux<String> events(AgentRun run, AguiRunRequest input) {
        return Flux.create(sink -> {
            var converter = new AguiEventConverter(input.threadId(), input.runId());
            run.onEvent(event -> converter.convert(event).forEach(agui -> send(sink, agui)));
            sink.onCancel(run::cancel);
            run.result().subscribe(
                    result -> {
                        converter.finish(result.content()).forEach(agui -> send(sink, agui));
                        sink.complete();
                    },
                    error -> {
                        converter.fail(error.getMessage(), CODE_RUN_FAILED).forEach(agui -> send(sink, agui));
                        sink.complete();
                    });
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    // 由 text/event-stream 编码器补 data: 前缀与空行，保持事件体为单行 JSON。
    private void send(FluxSink<String> sink, AguiEvent event) {
        sink.next(json(event));
    }

    private String json(AguiEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException error) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, CODE_ENCODE_FAILED, "AG-UI 事件无法序列化。", error);
        }
    }
}