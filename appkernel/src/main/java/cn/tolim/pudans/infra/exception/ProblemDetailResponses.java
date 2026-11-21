package cn.tolim.pudans.infra.exception;

import cn.tolim.pudans.infra.utils.PudansUtils;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

public final class ProblemDetailResponses {

    public static final String SIGN_IN_PATH = "/sign-in";

    public static final String TOO_MANY_REQUESTS_PATH = "/429";

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

    public static Mono<Void> writeOrRedirect(
            ServerWebExchange exchange,
            ServerResponse.Context context,
            HttpStatus status,
            String code,
            String detail,
            String redirectPath) {
        if (!PudansUtils.prefersHtml(exchange.getRequest().getHeaders())) {
            return write(exchange, context, status, code, detail);
        }
        var location = UriComponentsBuilder.fromPath(redirectPath)
                .queryParam("code", code)
                .queryParam("detail", detail)
                .build()
                .encode()
                .toUriString();
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(location));
        return response.setComplete();
    }
}
