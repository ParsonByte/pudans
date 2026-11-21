package cn.tolim.pudans.core.ai.provider.supplier;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import java.net.URI;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;

public class SupplierWebSocketHandler implements WebSocketHandler {

    private final SupplierWebSocketAccess access;

    private final ProviderAccounts providerAccounts;

    private final UnaryOperator<String> pathNormalizer;

    private final Pattern pathPattern;

    private final ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

    public SupplierWebSocketHandler(
            String supplierSegment,
            SupplierWebSocketAccess access,
            ProviderAccounts providerAccounts,
            UnaryOperator<String> pathNormalizer) {
        this.access = access;
        this.providerAccounts = providerAccounts;
        this.pathNormalizer = pathNormalizer;
        this.pathPattern = Pattern.compile("^" + Pattern.quote(ApiGroups.API_PREFIX)
                + "/aiproviders/([^/]+)/" + Pattern.quote(supplierSegment) + "/ws/?(.*)$");
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        var uri = session.getHandshakeInfo().getUri();
        var matcher = pathPattern.matcher(uri.getPath());
        if (!matcher.matches()) {
            return session.close(new CloseStatus(1008, "无效的供应商 WebSocket 路径"));
        }
        var providerName = matcher.group(1);
        var path = pathNormalizer.apply(matcher.group(2));
        return access
                .requireProvider(providerName)
                .flatMap(provider -> access
                        .requireAccount(provider)
                        .flatMap(account -> providerAccounts
                                .apiKey(account)
                                .defaultIfEmpty("")
                                .flatMap(apiKey -> proxy(session, provider, path, uri.getRawQuery(), apiKey))));
    }

    private Mono<Void> proxy(
            WebSocketSession downstream,
            AiProvider provider,
            String path,
            @Nullable String rawQuery,
            String apiKey) {
        var upstreamUri = upstreamUri(provider, path, rawQuery);
        var headers = new HttpHeaders();
        AiProviderHttpSupport.addProviderHeaders(headers, provider);
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
        return client
                .execute(upstreamUri, headers, upstream -> forward(downstream, upstream))
                .onErrorResume(error -> downstream.close(
                        new CloseStatus(1011, "供应商 WebSocket 上游连接失败")));
    }

    private Mono<Void> forward(WebSocketSession downstream, WebSocketSession upstream) {
        var toUpstream = downstream
                .receive()
                .map(message -> copy(upstream, message))
                .as(upstream::send)
                .doFinally(signal -> upstream.close().subscribe());
        var toDownstream = upstream
                .receive()
                .map(message -> copy(downstream, message))
                .as(downstream::send)
                .doFinally(signal -> downstream.close().subscribe());
        return Mono.when(toUpstream, toDownstream).then();
    }

    private static WebSocketMessage copy(WebSocketSession target, WebSocketMessage source) {
        var payload = source.getPayload();
        return switch (source.getType()) {
            case TEXT -> target.textMessage(source.getPayloadAsText());
            case BINARY -> target.binaryMessage(factory -> {
                var bytes = new byte[payload.readableByteCount()];
                payload.read(bytes);
                return factory.wrap(bytes);
            });
            case PING -> target.pingMessage(factory -> {
                var bytes = new byte[payload.readableByteCount()];
                payload.read(bytes);
                return factory.wrap(bytes);
            });
            case PONG -> target.pongMessage(factory -> {
                var bytes = new byte[payload.readableByteCount()];
                payload.read(bytes);
                return factory.wrap(bytes);
            });
        };
    }

    private static URI upstreamUri(
            AiProvider provider, String path, @Nullable String rawQuery) {
        var base = URI.create(provider.getSpec().getBaseUrl());
        var scheme = "https".equalsIgnoreCase(base.getScheme()) ? "wss" : "ws";
        try {
            return new URI(
                    scheme,
                    null,
                    base.getHost(),
                    base.getPort(),
                    path,
                    rawQuery,
                    null);
        } catch (java.net.URISyntaxException error) {
            throw new IllegalArgumentException("无法构建供应商 WebSocket 地址。", error);
        }
    }
}
