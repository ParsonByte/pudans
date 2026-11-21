package cn.tolim.pudans.core.ai.runtime.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import cn.tolim.pudans.contract.ai.AiSandboxProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.infra.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AliyunSandboxAdapterTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final List<ClientRequest> requests = new ArrayList<>();

    private final Queue<ClientResponse> responses = new ArrayDeque<>();

    private final SandboxProviderConfig config = new SandboxProviderConfig(
            AiSandboxProvider.Provider.ALIYUN,
            "阿里云沙箱",
            null,
            "cn-hangzhou",
            "access-id",
            "api-key-1",
            "image-default",
            3600);

    private final SandboxRuntimeSettings settings =
            new SandboxRuntimeSettings("/tmp/pudans-agent.log", "https://49983-%s.%s.tencentags.com", "2025-09-20", 30);

    @Test
    void createsASessionWithTheAgentImageAndTimeoutLimits() throws Exception {
        responses.add(json("""
                {"Success":true,"Code":"200","Data":{"SessionId":"session-1","LinkUrl":"https://mcp.example.com/session-1"}}
                """));
        var adapter = adapter();

        StepVerifier.create(adapter.create(new SandboxCreateSpec(
                        "agent-1", "image-agent", null, 1.0, 1024, 7200, true, "/workspace", Map.of())))
                .assertNext(handle -> {
                    assertThat(handle.sandboxId()).isEqualTo("session-1");
                    assertThat(handle.endpoint()).isEqualTo("https://mcp.example.com/session-1");
                })
                .verifyComplete();

        var request = requests.get(0);
        assertThat(request.url().toString())
                .startsWith("https://agentbay.cn-hangzhou.aliyuncs.com")
                .contains("Action=CreateMcpSession")
                .contains("Version=2025-05-06");
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer api-key-1");
        var body = mapper.readTree(body(request));
        assertThat(body.path("ImageId").asText()).isEqualTo("image-agent");
        assertThat(body.path("Timeout").asInt()).isEqualTo(3600);
        assertThat(body.path("MaxRuntime").asInt()).isEqualTo(3600);
        assertThat(body.path("ManualRelease").asBoolean()).isTrue();
        assertThat(body.path("Authorization").asText()).isEqualTo("Bearer api-key-1");
    }

    @Test
    void injectsEnvironmentAndStartsTheEntrypointAfterCreatingTheSession() throws Exception {
        responses.add(json("""
                {"Success":true,"Code":"200","Data":{"SessionId":"session-2"}}
                """));
        responses.add(toolData("{\"success\":true}"));
        responses.add(toolData("{\"exit_code\":0,\"stdout\":\"\",\"stderr\":\"\"}"));

        StepVerifier.create(adapter().create(new SandboxCreateSpec(
                        "agent-1",
                        "image-agent",
                        "python app.py",
                        1.0,
                        1024,
                        600,
                        true,
                        "/workspace",
                        Map.of("PUDANS_AGENT_NAME", "agent-1"))))
                .assertNext(handle -> assertThat(handle.sandboxId()).isEqualTo("session-2"))
                .verifyComplete();

        assertThat(requests).hasSize(3);
        var envBody = mapper.readTree(body(requests.get(1)));
        assertThat(envBody.path("Name").asText()).isEqualTo("write_file");
        var envArgs = mapper.readTree(envBody.path("Args").asText());
        assertThat(envArgs.path("path").asText()).isEqualTo("/workspace/.pudans-runtime.env");
        assertThat(envArgs.path("content").asText()).contains("export PUDANS_AGENT_NAME=agent-1");

        var entrypointBody = mapper.readTree(body(requests.get(2)));
        assertThat(entrypointBody.path("Name").asText()).isEqualTo("shell");
        var entrypointArgs = mapper.readTree(entrypointBody.path("Args").asText());
        assertThat(entrypointArgs.path("command").asText())
                .isEqualTo("nohup python app.py >> /tmp/pudans-agent.log 2>&1 &");
        assertThat(entrypointArgs.path("cwd").asText()).isEqualTo("/workspace");
        assertThat(entrypointArgs.path("timeout_ms").asInt()).isEqualTo(30000);
    }

    @Test
    void executesAShellCommandAndParsesTheWrappedResult() throws Exception {
        responses.add(toolData("{\"exit_code\":3,\"stdout\":\"hello\\n\",\"stderr\":\"warn\\n\"}"));
        var adapter = adapter();

        StepVerifier.create(adapter.exec(
                        "session-1",
                        new SandboxExecSpec("echo", List.of("hello world"), "/workspace", Map.of("FOO", "bar"), 5)))
                .assertNext(result -> {
                    assertThat(result.exitCode()).isEqualTo(3);
                    assertThat(result.stdout()).isEqualTo("hello\n");
                    assertThat(result.stderr()).isEqualTo("warn\n");
                    assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
                })
                .verifyComplete();

        var request = requests.get(0);
        assertThat(request.url().toString()).contains("Action=CallMcpTool");
        var body = mapper.readTree(body(request));
        assertThat(body.path("SessionId").asText()).isEqualTo("session-1");
        assertThat(body.path("Name").asText()).isEqualTo("shell");
        var args = mapper.readTree(body.path("Args").asText());
        assertThat(args.path("command").asText())
                .isEqualTo("set -a; if [ -f /workspace/.pudans-runtime.env ]; then . "
                        + "/workspace/.pudans-runtime.env; fi; set +a; exec echo 'hello world'");
        assertThat(args.path("timeout_ms").asInt()).isEqualTo(5000);
        assertThat(args.path("cwd").asText()).isEqualTo("/workspace");
        assertThat(args.path("envs").path("FOO").asText()).isEqualTo("bar");
    }

    @Test
    void readsABinaryFileAndDecodesTheBase64Payload() throws Exception {
        responses.add(json("""
                {"Success":true,"Code":"200","Data":"aGVsbG8="}
                """));
        var adapter = adapter();

        StepVerifier.create(adapter.readFile("session-1", "/workspace/hello.txt"))
                .assertNext(file -> {
                    assertThat(file.encoding()).isEqualTo(SandboxFiles.UTF8);
                    assertThat(file.content()).isEqualTo("hello");
                    assertThat(file.size()).isEqualTo(5);
                })
                .verifyComplete();

        var body = mapper.readTree(body(requests.get(0)));
        assertThat(body.path("Name").asText()).isEqualTo("read_file");
        var args = mapper.readTree(body.path("Args").asText());
        assertThat(args.path("path").asText()).isEqualTo("/workspace/hello.txt");
        assertThat(args.path("format").asText()).isEqualTo("binary");
    }

    @Test
    void rejectsBinaryWrites() {
        var adapter = adapter();

        StepVerifier.create(adapter.writeFile("session-1", "/workspace/data.bin", new byte[] {(byte) 0xFF, (byte) 0xFE}))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo(SandboxErrors.CONFIG_INVALID);
                })
                .verify();

        assertThat(requests).isEmpty();
    }

    @Test
    void destroysTheSessionAndMapsStatus() throws Exception {
        responses.add(json("""
                {"Success":true,"Code":"200","Data":{}}
                """));
        responses.add(json("""
                {"Success":true,"Code":"200","Data":{"Status":"PAUSED"}}
                """));
        var adapter = adapter();

        StepVerifier.create(adapter.destroy("session-1")).verifyComplete();
        StepVerifier.create(adapter.status("session-1"))
                .assertNext(status -> {
                    assertThat(status.state()).isEqualTo(SandboxState.STOPPED);
                    assertThat(status.message()).isEqualTo("PAUSED");
                })
                .verifyComplete();

        var destroyBody = mapper.readTree(body(requests.get(0)));
        assertThat(destroyBody.path("SessionId").asText()).isEqualTo("session-1");
        assertThat(requests.get(0).url().toString()).contains("Action=DeleteSessionAsync");
    }

    @Test
    void mapsProviderRejectionToAStableErrorCode() {
        responses.add(json("""
                {"Success":false,"Code":"InvalidApiKey","Message":"The API key is invalid."}
                """));
        var adapter = adapter();

        StepVerifier.create(adapter.create(new SandboxCreateSpec(
                        "agent-1", "image-agent", null, 1.0, 1024, 600, true, "/workspace", Map.of())))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var api = (ApiException) error;
                    assertThat(api.getCode()).isEqualTo(SandboxErrors.PROVIDER_ERROR);
                    assertThat(api.getReason()).contains("InvalidApiKey");
                })
                .verify();
    }

    @Test
    void failsFastWhenNoImageIsConfigured() {
        var adapter = new AliyunSandboxAdapter(
                webClient(),
                mapper,
                new SandboxProviderConfig(
                        AiSandboxProvider.Provider.ALIYUN,
                        "阿里云沙箱",
                        null,
                        "cn-hangzhou",
                        null,
                        "api-key-1",
                        null,
                        3600),
                settings);

        StepVerifier.create(adapter.create(new SandboxCreateSpec(
                        "agent-1", null, null, 1.0, 1024, 600, true, "/workspace", Map.of())))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo(SandboxErrors.CONFIG_INVALID);
                })
                .verify();

        assertThat(requests).isEmpty();
    }

    private AliyunSandboxAdapter adapter() {
        return new AliyunSandboxAdapter(webClient(), mapper, config, settings);
    }

    private WebClient webClient() {
        var exchange = (ExchangeFunction) request -> {
            requests.add(request);
            var response = responses.poll();
            return response == null ? Mono.error(new IllegalStateException("未配置响应")) : Mono.just(response);
        };
        return WebClient.builder().exchangeFunction(exchange).build();
    }

    private static ClientResponse toolData(String data) throws Exception {
        var root = JsonUtils.mapper().createObjectNode();
        root.put("Success", true);
        root.put("Code", "200");
        root.put("Data", data);
        return json(JsonUtils.mapper().writeValueAsString(root));
    }

    private static ClientResponse json(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static String body(ClientRequest request) {
        var concrete = new MockClientHttpRequest(request.method(), request.url());
        request.writeTo(concrete, ExchangeStrategies.withDefaults()).block();
        return concrete.getBodyAsString().block();
    }
}