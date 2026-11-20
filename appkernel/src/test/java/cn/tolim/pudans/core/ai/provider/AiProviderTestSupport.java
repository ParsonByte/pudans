package cn.tolim.pudans.core.ai.provider;

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

    public static String body(ClientRequest request) {
        var concrete = new MockClientHttpRequest(request.method(), request.url());
        request.writeTo(concrete, ExchangeStrategies.withDefaults()).block();
        return concrete.getBodyAsString().block();
    }
}
