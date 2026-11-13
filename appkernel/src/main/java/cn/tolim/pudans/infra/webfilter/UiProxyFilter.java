package cn.tolim.pudans.infra.webfilter;

import cn.tolim.pudans.infra.properties.UiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * Development proxy aligned with Halo's {@code halo.ui.proxy}: when enabled,
 * HTML entry requests are forwarded to the running Vite server on port 3000
 * instead of the packaged static files. The browser therefore keeps using
 * {@code http://localhost:8090/console} and never treats the Vite port as the
 * production entry.
 */
@Slf4j
public class UiProxyFilter implements WebFilter {

    private static final String[] UI_PREFIXES = {"/console", "/uc", "/auth", "/setup", "/forbidden"};

    private final WebClient webClient;

    public UiProxyFilter(UiProperties.Proxy properties) {
        this.webClient = WebClient.create(properties.getEndpoint().toString());
        log.debug("UI dev proxy enabled for {} -> {}", String.join(", ", UI_PREFIXES), properties.getEndpoint());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        if (!HttpMethod.GET.equals(request.getMethod()) || !isUiRequest(request.getPath().value()) || !acceptsHtml(exchange)) {
            return chain.filter(exchange);
        }

        var uri = UriComponentsBuilder.fromPath(request.getPath().value())
                .queryParams(request.getQueryParams())
                .build()
                .toUriString();

        return webClient
                .get()
                .uri(uri)
                .headers(headers -> headers.addAll(request.getHeaders()))
                .exchangeToMono(clientResponse -> {
                    var response = exchange.getResponse();
                    response.getHeaders().putAll(clientResponse.headers().asHttpHeaders());
                    response.getCookies().putAll(clientResponse.cookies());
                    response.setStatusCode(clientResponse.statusCode());

                    var status = clientResponse.statusCode();
                    var contentLength = clientResponse.headers().contentLength().orElse(0L);
                    if (status.is3xxRedirection() || status.equals(HttpStatus.NO_CONTENT) || contentLength == 0) {
                        return Mono.empty();
                    }
                    return response.writeWith(clientResponse.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class));
                });
    }

    private static boolean isUiRequest(String path) {
        for (var prefix : UI_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean acceptsHtml(ServerWebExchange exchange) {
        var accept = exchange.getRequest().getHeaders().getAccept();
        return accept.isEmpty() || accept.stream().anyMatch(mediaType -> mediaType.isCompatibleWith(MediaType.TEXT_HTML));
    }
}
