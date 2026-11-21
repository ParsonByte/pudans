package cn.tolim.pudans.core.ai.runtime.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.AiSandboxProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class E2BSandboxAdapterTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final List<ClientRequest> requests = new ArrayList<>();

    private final Queue<ClientResponse> responses = new ArrayDeque<>();

    private final SandboxProviderConfig config = new SandboxProviderConfig(
            AiSandboxProvider.Provider.E2B,
            "E2B",
            null,
            null,
            null,
            "api-key",
            "template-1",
            3600);

    private final SandboxRuntimeSettings settings =
            new SandboxRuntimeSettings("/tmp/pudans-agent.log", "https://49983-%s.%s.tencentags.com", "2025-09-20", 30);

    @Test
    void createsASandboxAndResolvesTheEnvdEndpoint() throws Exception {
        responses.add(control("{\"sandboxID\":\"sbx-1\"}"));
        responses.add(control("{\"envdAccessToken\":\"token-1\",\"domain\":\"e2b.app\"}"));
        var adapter = adapter();

        StepVerifier.create(adapter.create(new SandboxCreateSpec(
                        "agent-1", null, null, 1.0, 1024, 7200, true, "/workspace", Map.of())))
                .assertNext(handle -> {
                    assertThat(handle.sandboxId()).isEqualTo("sbx-1");
                    assertThat(handle.endpoint()).isEqualTo("https://49983-sbx-1.e2b.app");
                })
                .verifyComplete();

        var create = requests.get(0);
        assertThat(create.url().toString()).isEqualTo("https://api.e2b.dev/sandboxes");
        assertThat(create.headers().getFirst("X-API-Key")).isEqualTo("api-key");
        var body = mapper.readTree(body(create));
        assertThat(body.path("templateID").asText()).isEqualTo("template-1");
        assertThat(body.path("timeout").asInt()).isEqualTo(3600);
        assertThat(requests.get(1).url().toString())
                .isEqualTo("https://api.e2b.dev/sandboxes/sbx-1/connect");
    }

    @Test
    void executesCommandsThroughEnvd() throws Exception {
        responses.add(connect("{\"envdAccessToken\":\"token-1\",\"domain\":\"e2b.app\"}"));
        responses.add(connectBody("aGVsbG8=", "", 0, null));
        var adapter = adapter();

        StepVerifier.create(adapter.exec(
                        "sbx-1",
                        new SandboxExecSpec("echo", List.of("hello"), "/workspace", Map.of(), 30)))
                .assertNext(result -> {
                    assertThat(result.exitCode()).isZero();
                    assertThat(result.stdout()).isEqualTo("hello");
                })
                .verifyComplete();

        var exec = requests.get(1);
        assertThat(exec.url().toString()).isEqualTo("https://49983-sbx-1.e2b.app/process.Process/Start");
        assertThat(exec.headers().getFirst("X-Access-Token")).isEqualTo("token-1");
    }

    private E2BSandboxAdapter adapter() {
        var exchange = (ExchangeFunction) request -> {
            requests.add(request);
            var response = responses.poll();
            return response == null ? Mono.error(new IllegalStateException("未配置响应")) : Mono.just(response);
        };
        var webClient = WebClient.builder().exchangeFunction(exchange).build();
        return new E2BSandboxAdapter(webClient, mapper, config, settings);
    }

    private static ClientResponse control(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static ClientResponse connect(String body) {
        return control(body);
    }

    private static ClientResponse connectBody(String stdoutBase64, String stderrBase64, int exitCode, String error)
            throws Exception {
        var payload = new ByteArrayOutputStream();
        var data = JsonUtils.mapper().createObjectNode();
        var dataNode = data.putObject("event").putObject("data");
        dataNode.put("stdout", stdoutBase64);
        dataNode.put("stderr", stderrBase64);
        payload.writeBytes(ConnectStreamCodec.frame(JsonUtils.mapper().writeValueAsBytes(data)));
        var end = JsonUtils.mapper().createObjectNode();
        var endNode = end.putObject("event").putObject("end");
        endNode.put("exitCode", exitCode);
        if (error != null) {
            endNode.put("error", error);
        }
        payload.writeBytes(ConnectStreamCodec.frame(JsonUtils.mapper().writeValueAsBytes(end)));
        var buffer = DefaultDataBufferFactory.sharedInstance.wrap(payload.toByteArray());
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "application/connect+json")
                .body(Flux.<DataBuffer>just(buffer))
                .build();
    }

    private static String body(ClientRequest request) {
        var concrete = new MockClientHttpRequest(request.method(), request.url());
        request.writeTo(concrete, ExchangeStrategies.withDefaults()).block();
        return concrete.getBodyAsString().block();
    }
}