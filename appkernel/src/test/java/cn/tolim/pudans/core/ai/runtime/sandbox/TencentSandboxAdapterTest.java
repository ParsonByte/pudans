package cn.tolim.pudans.core.ai.runtime.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.AiSandboxProvider;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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

class TencentSandboxAdapterTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final List<ClientRequest> requests = new ArrayList<>();

    private final Queue<ClientResponse> responses = new ArrayDeque<>();

    private final SandboxProviderConfig config = new SandboxProviderConfig(
            AiSandboxProvider.Provider.TENCENT,
            "腾讯云沙箱",
            null,
            "ap-guangzhou",
            "secret-id",
            "secret-key",
            "sdt-tool-1",
            3600);

    private final SandboxRuntimeSettings settings =
            new SandboxRuntimeSettings("/tmp/pudans-agent.log", "https://49983-%s.%s.tencentags.com", "2025-09-20", 30);

    @Test
    void startsASandboxInstanceWithSignedControlRequests() throws Exception {
        responses.add(control("""
                {"Response":{"Instance":{"InstanceId":"ins-1"},"RequestId":"r1"}}
                """));
        var adapter = adapter();

        StepVerifier.create(adapter.create(new SandboxCreateSpec(
                        "agent-1", null, null, 1.0, 1024, 7200, true, "/workspace", Map.of())))
                .assertNext(handle -> {
                    assertThat(handle.sandboxId()).isEqualTo("ins-1");
                    assertThat(handle.endpoint()).isEqualTo("https://49983-ins-1.ap-guangzhou.tencentags.com");
                })
                .verifyComplete();

        var request = requests.get(0);
        assertThat(request.url().toString()).isEqualTo("https://ags.ap-guangzhou.tencentcloudapi.com/");
        assertThat(request.headers().getFirst("X-TC-Action")).isEqualTo("StartSandboxInstance");
        assertThat(request.headers().getFirst("X-TC-Version")).isEqualTo("2025-09-20");
        assertThat(request.headers().getFirst("X-TC-Region")).isEqualTo("ap-guangzhou");
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION))
                .startsWith("TC3-HMAC-SHA256 Credential=secret-id/")
                .doesNotContain("secret-key");
        var body = mapper.readTree(body(request));
        assertThat(body.path("ToolId").asText()).isEqualTo("sdt-tool-1");
        assertThat(body.path("Timeout").asText()).isEqualTo("3600s");
        assertThat(body.path("AuthMode").asText()).isEqualTo("TOKEN");
        assertThat(body.path("NetworkMode").asText()).isEqualTo("PUBLIC");
        assertThat(body.path("CustomConfiguration").path("Resources").path("CPU").asText())
                .isEqualTo("1");
        assertThat(body.path("CustomConfiguration").path("Resources").path("Memory").asText())
                .isEqualTo("1024Mi");
        assertThat(body.has("Command")).isFalse();
        assertThat(body.has("Args")).isFalse();
    }

    @Test
    void mapsEnvironmentAndImageIntoTheCustomConfiguration() throws Exception {
        responses.add(control("""
                {"Response":{"Instance":{"InstanceId":"ins-2"}}}
                """));
        var adapter = adapter();

        StepVerifier.create(adapter.create(new SandboxCreateSpec(
                        "agent-1",
                        "image-agent",
                        null,
                        0.5,
                        2048,
                        600,
                        false,
                        "/workspace",
                        Map.of("B_VAR", "two", "A_VAR", "one"))))
                .assertNext(handle -> assertThat(handle.sandboxId()).isEqualTo("ins-2"))
                .verifyComplete();

        var body = mapper.readTree(body(requests.get(0)));
        assertThat(body.path("NetworkMode").asText()).isEqualTo("SANDBOX");
        var custom = body.path("CustomConfiguration");
        assertThat(custom.path("Image").asText()).isEqualTo("image-agent");
        assertThat(custom.path("Resources").path("CPU").asText()).isEqualTo("500m");
        assertThat(custom.path("Resources").path("Memory").asText()).isEqualTo("2048Mi");
        assertThat(custom.path("Env")).hasSize(2);
        assertThat(custom.path("Env").get(0).path("Name").asText()).isEqualTo("A_VAR");
        assertThat(custom.path("Env").get(0).path("Value").asText()).isEqualTo("one");
        assertThat(custom.path("Env").get(1).path("Name").asText()).isEqualTo("B_VAR");
    }

    @Test
    void executesCommandsThroughTheConnectDataPlane() throws Exception {
        responses.add(control("""
                {"Response":{"Token":"token-1"}}
                """));
        responses.add(connect(connectBody("aGVsbG8=", "", 7, null)));
        var adapter = adapter();

        StepVerifier.create(adapter.exec(
                        "ins-1",
                        new SandboxExecSpec("echo", List.of("hello"), "/workspace", Map.of("FOO", "bar"), 30)))
                .assertNext(result -> {
                    assertThat(result.exitCode()).isEqualTo(7);
                    assertThat(result.stdout()).isEqualTo("hello");
                    assertThat(result.stderr()).isEmpty();
                    assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
                })
                .verifyComplete();

        assertThat(requests).hasSize(2);
        var tokenRequest = requests.get(0);
        assertThat(tokenRequest.headers().getFirst("X-TC-Action")).isEqualTo("AcquireSandboxInstanceToken");
        assertThat(mapper.readTree(body(tokenRequest)).path("InstanceId").asText()).isEqualTo("ins-1");

        var execRequest = requests.get(1);
        assertThat(execRequest.url().toString())
                .isEqualTo("https://49983-ins-1.ap-guangzhou.tencentags.com/process.Process/Start");
        assertThat(execRequest.headers().getFirst("X-Access-Token")).isEqualTo("token-1");
        assertThat(execRequest.headers().getFirst("Connect-Protocol-Version")).isEqualTo("1");
        assertThat(execRequest.headers().getFirst("Connect-Timeout-Ms")).isEqualTo("30000");
        assertThat(execRequest.headers().getContentType()).isEqualTo(MediaType.parseMediaType("application/connect+json"));
        var payload = mapper.readTree(unframed(execRequest));
        assertThat(payload.path("process").path("cmd").asText()).isEqualTo("/bin/bash");
        assertThat(payload.path("process").path("args").get(0).asText()).isEqualTo("-l");
        assertThat(payload.path("process").path("args").get(1).asText()).isEqualTo("-c");
        assertThat(payload.path("process").path("args").get(2).asText()).isEqualTo("echo hello");
        assertThat(payload.path("process").path("cwd").asText()).isEqualTo("/workspace");
        assertThat(payload.path("process").path("envs").path("FOO").asText()).isEqualTo("bar");
        assertThat(payload.path("stdin").asBoolean()).isFalse();
    }

    @Test
    void mapsTheExitErrorFromTheEndEventToStderr() throws Exception {
        responses.add(control("""
                {"Response":{"Token":"token-1"}}
                """));
        responses.add(connect(connectBody("", "", 1, "command not found")));
        var adapter = adapter();

        StepVerifier.create(adapter.exec(
                        "ins-1", new SandboxExecSpec("missing", List.of(), null, Map.of(), 30)))
                .assertNext(result -> {
                    assertThat(result.exitCode()).isEqualTo(1);
                    assertThat(result.stderr()).isEqualTo("command not found");
                })
                .verifyComplete();
    }

    @Test
    void stopsAnInstanceAndMapsItsStatus() throws Exception {
        responses.add(control("""
                {"Response":{}}
                """));
        responses.add(control("""
                {"Response":{"InstanceSet":[{"Status":"RUNNING"}]}}
                """));
        responses.add(control("""
                {"Response":{"InstanceSet":[]}}
                """));
        var adapter = adapter();

        StepVerifier.create(adapter.destroy("ins-1")).verifyComplete();
        StepVerifier.create(adapter.status("ins-1"))
                .assertNext(status -> assertThat(status.state()).isEqualTo(SandboxState.RUNNING))
                .verifyComplete();
        StepVerifier.create(adapter.status("ins-missing"))
                .assertNext(status -> {
                    assertThat(status.state()).isEqualTo(SandboxState.DESTROYED);
                    assertThat(status.message()).isEqualTo("NOT_FOUND");
                })
                .verifyComplete();

        assertThat(requests.get(0).headers().getFirst("X-TC-Action")).isEqualTo("StopSandboxInstance");
        assertThat(mapper.readTree(body(requests.get(0))).path("InstanceId").asText()).isEqualTo("ins-1");
        assertThat(requests.get(1).headers().getFirst("X-TC-Action")).isEqualTo("DescribeSandboxInstanceList");
    }

    @Test
    void mapsControlPlaneErrorsToStableCodes() {
        responses.add(control("""
                {"Response":{"Error":{"Code":"InvalidParameter","Message":"ToolId is invalid."}}}
                """));
        var adapter = adapter();

        StepVerifier.create(adapter.create(new SandboxCreateSpec(
                        "agent-1", null, null, 1.0, 1024, 600, true, "/workspace", Map.of())))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(cn.tolim.pudans.infra.exception.ApiException.class);
                    var api = (cn.tolim.pudans.infra.exception.ApiException) error;
                    assertThat(api.getCode()).isEqualTo(SandboxErrors.PROVIDER_ERROR);
                    assertThat(api.getReason()).contains("InvalidParameter");
                })
                .verify();
    }

    private TencentSandboxAdapter adapter() {
        var exchange = (ExchangeFunction) request -> {
            requests.add(request);
            var response = responses.poll();
            return response == null ? Mono.error(new IllegalStateException("未配置响应")) : Mono.just(response);
        };
        var webClient = WebClient.builder().exchangeFunction(exchange).build();
        return new TencentSandboxAdapter(webClient, mapper, config, settings);
    }

    private static ClientResponse control(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static ClientResponse connect(byte[] body) {
        var buffer = DefaultDataBufferFactory.sharedInstance.wrap(body);
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "application/connect+json")
                .body(Flux.<DataBuffer>just(buffer))
                .build();
    }

    private byte[] connectBody(
            String stdoutBase64, String stderrBase64, int exitCode, String error) throws Exception {
        var payload = new ByteArrayOutputStream();
        var data = mapper.createObjectNode();
        var dataNode = data.putObject("event").putObject("data");
        dataNode.put("stdout", stdoutBase64);
        dataNode.put("stderr", stderrBase64);
        payload.writeBytes(ConnectStreamCodec.frame(mapper.writeValueAsBytes(data)));
        var end = mapper.createObjectNode();
        var endNode = end.putObject("event").putObject("end");
        endNode.put("exitCode", exitCode);
        if (error != null) {
            endNode.put("error", error);
        }
        payload.writeBytes(ConnectStreamCodec.frame(mapper.writeValueAsBytes(end)));
        return payload.toByteArray();
    }

    private static String body(ClientRequest request) {
        var concrete = new MockClientHttpRequest(request.method(), request.url());
        request.writeTo(concrete, ExchangeStrategies.withDefaults()).block();
        return concrete.getBodyAsString().block();
    }

    private static String unframed(ClientRequest request) {
        var bytes = body(request).getBytes(StandardCharsets.UTF_8);
        var envelopes = ConnectStreamCodec.unframe(bytes);
        return new String(envelopes.get(0).payload(), StandardCharsets.UTF_8);
    }
}