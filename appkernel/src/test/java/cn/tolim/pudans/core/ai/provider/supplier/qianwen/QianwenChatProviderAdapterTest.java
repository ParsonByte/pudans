package cn.tolim.pudans.core.ai.provider.supplier.qianwen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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

class QianwenChatProviderAdapterTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiProviderAccountService accountService = mock(AiProviderAccountService.class);

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    @Test
    void chatsThroughTheCompatibleModeEndpoint() {
        when(accountService.apiKey(any())).thenReturn(Mono.just("sk-secret"));
        var adapter = adapter("""
                {"id":"chat-1","model":"qwen3-max","choices":[
                  {"index":0,"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}
                ],"usage":{"prompt_tokens":3,"completion_tokens":1}}
                """);

        StepVerifier.create(adapter.chat(provider(), account(), model(), request()))
                .assertNext(response -> {
                    assertThat(response.model()).isEqualTo("qwen3-max");
                    assertThat(response.message().parts()).containsExactly(
                            new AiContentPart.Text("OK", null));
                })
                .verifyComplete();

        assertThat(captured.get().url().toString())
                .isEqualTo("https://maas.example.com/compatible-mode/v1/chat/completions");
        assertThat(captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer sk-secret");
        assertThat(body(captured.get())).contains("\"model\":\"qwen3-max\"");
    }

    private QianwenChatProviderAdapter adapter(String body) {
        var exchange = (ExchangeFunction) request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        };
        return new QianwenChatProviderAdapter(
                WebClient.builder().exchangeFunction(exchange).build(), mapper, accountService);
    }

    private static AiChatIr request() {
        return new AiChatIr(
                List.of(new AiChatMessage(
                        "user", List.of(new AiContentPart.Text("你好", null)))),
                List.of(),
                new AiRequestParams(null, 0.0, 16, null, null, null, null, Map.of()),
                false);
    }

    private static AiProvider provider() {
        var provider = new AiProvider();
        provider.getSpec().setProtocol(AiProvider.Protocol.QIANWEN);
        provider.getSpec().setBaseUrl("https://maas.example.com");
        return provider;
    }

    private static AiProviderAccount account() {
        return new AiProviderAccount();
    }

    private static AiModel model() {
        var model = new AiModel();
        model.getSpec().setModelCode("qwen3-max");
        return model;
    }

    private static String body(ClientRequest request) {
        var concrete = new MockClientHttpRequest(request.method(), request.url());
        request.writeTo(concrete, ExchangeStrategies.withDefaults()).block();
        return concrete.getBodyAsString().block();
    }
}
