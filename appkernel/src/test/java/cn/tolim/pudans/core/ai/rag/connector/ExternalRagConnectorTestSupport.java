package cn.tolim.pudans.core.ai.rag.connector;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

final class ExternalRagConnectorTestSupport {

    private ExternalRagConnectorTestSupport() {}

    static ClientResponse error(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    static final class Recorder implements ExchangeFunction {

        private final ClientResponse response;

        private ClientRequest request;

        Recorder(ClientResponse response) {
            this.response = response;
        }

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            this.request = request;
            return Mono.just(response);
        }

        ClientRequest request() {
            return request;
        }

        WebClient webClient() {
            return WebClient.builder().exchangeFunction(this).build();
        }
    }
}