package cn.tolim.pudans.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public final class ProblemDetailResponses {

    private ProblemDetailResponses() {}

    public static Mono<Void> write(
            ServerWebExchange exchange,
            ServerResponse.Context context,
            HttpStatus status,
            String code,
            String detail) {
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(ProblemDetailFactory.create(status, code, detail))
                .flatMap(response -> response.writeTo(exchange, context));
    }
}
