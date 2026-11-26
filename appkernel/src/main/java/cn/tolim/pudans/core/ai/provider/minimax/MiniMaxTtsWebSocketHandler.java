package cn.tolim.pudans.core.ai.provider.minimax;

import cn.tolim.pudans.core.ai.gateway.AiChannels;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import cn.tolim.pudans.security.authorization.RequestAuthorizer;
import cn.tolim.pudans.security.authorization.RequestInfo;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MiniMaxTtsWebSocketHandler implements WebSocketHandler {

    public static final String TTS_PATTERN =
            ApiGroups.API_PREFIX + "/aichannels/{name}/-/minimax/tts-websocket";

    public static final String BIDI_PATTERN =
            ApiGroups.API_PREFIX + "/aichannels/{name}/-/minimax/tts-websocket-bidi";

    private static final String TTS_SUFFIX = "/-/minimax/tts-websocket";

    private static final String BIDI_SUFFIX = "/-/minimax/tts-websocket-bidi";

    private static final String CHANNEL_MARKER = "/aichannels/";

    private static final String API_KEY = "apiKey";

    private final AiChannels channels;

    private final MiniMaxProvider provider;

    private final RequestAuthorizer authorizer;

    private final WebSocketClient upstreamClient;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return context(session)
                .flatMap(context -> authorizer
                        .allows(context.authentication(), requestInfo(context))
                        .flatMap(allowed -> Boolean.TRUE.equals(allowed)
                                ? relay(session, context)
                                : deny(session)))
                .onErrorResume(error -> fail(session, error));
    }

    // 从握手请求提取工作区、渠道与认证主体。
    private Mono<SocketContext> context(WebSocketSession session) {
        var handshake = session.getHandshakeInfo();
        var path = handshake.getUri().getPath();
        var bidirectional = path.endsWith(BIDI_SUFFIX);
        if (!bidirectional && !path.endsWith(TTS_SUFFIX)) {
            return Mono.error(invalid("语音合成地址无效。"));
        }
        var workspace = handshake.getHeaders()
                .getFirst(ReactiveRequestIdentity.WORKSPACE_HEADER);
        if (!StringUtils.hasText(workspace)) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "request-workspace-missing",
                    "缺少当前工作区请求头。"));
        }
        var channelId = channelId(path);
        return handshake
                .getPrincipal()
                .filter(Authentication.class::isInstance)
                .map(Authentication.class::cast)
                .switchIfEmpty(ReactiveSecurityContextHolder.getContext()
                        .mapNotNull(SecurityContext::getAuthentication)
                        .filter(Authentication::isAuthenticated))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "request-owner-missing",
                        "当前请求没有已认证用户。")))
                .map(authentication -> new SocketContext(
                        authentication, workspace.trim(), channelId, bidirectional, path));
    }

    // WebSocket 握手是 GET，这里按语音合成的写动作重新做一次渠道授权。
    static RequestInfo requestInfo(SocketContext context) {
        var channelId = context.channelId().toString();
        return new RequestInfo(
                true,
                context.path(),
                "",
                "",
                "create",
                "apis",
                ApiGroups.API,
                ApiGroups.VERSION,
                "aichannels",
                channelId,
                "-",
                "minimax",
                new String[] {
                    "aichannels",
                    channelId,
                    "-",
                    "minimax",
                    context.bidirectional() ? "tts-websocket-bidi" : "tts-websocket"
                });
    }

    private Mono<Void> relay(WebSocketSession session, SocketContext context) {
        return channels
                .resolve(context.workspaceId(), context.channelId())
                .filter(resolved -> provider.descriptor().provider()
                        .equals(resolved.channel().getProvider()))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ai-channel-provider-unsupported",
                        "该渠道的服务商不是 minimax。")))
                .flatMap(resolved -> {
                    var apiKey = resolved.credentials().get(API_KEY);
                    if (!StringUtils.hasText(apiKey)) {
                        return Mono.error(new ApiException(
                                HttpStatus.CONFLICT,
                                "ai-provider-credentials-missing",
                                "AI 渠道缺少 apiKey 凭据。"));
                    }
                    var uri = URI.create(provider.ttsWebSocketUrl(
                            resolved.channel().getBaseUrl(), context.bidirectional()));
                    var headers = new HttpHeaders();
                    headers.setBearerAuth(apiKey.trim());
                    return upstreamClient.execute(
                            uri, headers, upstream -> bridge(session, upstream));
                });
    }

    // 双向转发文本与心跳帧，任一侧结束都关闭整条链路。
    private static Mono<Void> bridge(WebSocketSession client, WebSocketSession upstream) {
        var toUpstream = upstream
                .send(frames(client, upstream))
                .doFinally(signal -> closeQuietly(client));
        var toClient = client
                .send(frames(upstream, client))
                .doFinally(signal -> closeQuietly(upstream));
        return Mono.when(toUpstream, toClient)
                .onErrorResume(error -> upstream
                        .close()
                        .onErrorResume(closeError -> Mono.empty())
                        .then(Mono.error(error)));
    }

    private static Flux<WebSocketMessage> frames(
            WebSocketSession source, WebSocketSession target) {
        return source.receive().handle((message, sink) -> {
            if (message.getType() == WebSocketMessage.Type.TEXT) {
                var text = message.getPayloadAsText();
                DataBufferUtils.release(message.getPayload());
                sink.next(target.textMessage(text));
                return;
            }
            if (message.getType() == WebSocketMessage.Type.PING) {
                var payload = payload(message);
                sink.next(target.pingMessage(buffer -> buffer.wrap(payload)));
                return;
            }
            if (message.getType() == WebSocketMessage.Type.PONG) {
                var payload = payload(message);
                sink.next(target.pongMessage(buffer -> buffer.wrap(payload)));
                return;
            }
            DataBufferUtils.release(message.getPayload());
        });
    }

    private static void closeQuietly(WebSocketSession session) {
        session.close().onErrorResume(error -> Mono.empty()).subscribe();
    }

    private static byte[] payload(WebSocketMessage message) {
        var buffer = message.getPayload();
        var bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return bytes;
    }

    private static Mono<Void> deny(WebSocketSession session) {
        return sendAndClose(
                session,
                "当前用户无权使用该渠道的语音合成能力。",
                1008,
                CloseStatus.POLICY_VIOLATION);
    }

    // 在关闭前把平台错误转换成 MiniMax 的 task_failed 事件。
    private static Mono<Void> fail(WebSocketSession session, Throwable error) {
        var code = 1000;
        var message = error.getMessage();
        if (error instanceof ApiException api) {
            if (api.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                code = 1004;
            }
            if (StringUtils.hasText(api.getReason())) {
                message = api.getReason();
            }
        }
        return sendAndClose(
                session,
                StringUtils.hasText(message) ? message : "语音合成连接失败。",
                code,
                CloseStatus.SERVER_ERROR);
    }

    private static Mono<Void> sendAndClose(
            WebSocketSession session, String message, int code, CloseStatus status) {
        if (!session.isOpen()) {
            return Mono.empty();
        }
        var payload = JsonUtils.objectToJson(Map.of(
                "event",
                "task_failed",
                "base_resp",
                Map.of("status_code", code, "status_msg", message)));
        return session
                .send(Mono.just(session.textMessage(payload)))
                .onErrorResume(error -> Mono.empty())
                .then(session.close(status))
                .onErrorResume(error -> Mono.empty());
    }

    private static UUID channelId(String path) {
        var start = path.indexOf(CHANNEL_MARKER);
        if (start < 0) {
            throw invalid("渠道标识缺失。");
        }
        var remainder = path.substring(start + CHANNEL_MARKER.length());
        var end = remainder.indexOf('/');
        var value = end < 0 ? remainder : remainder.substring(0, end);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw invalid("渠道标识不是有效 UUID。");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST, "ai-channel-id-invalid", message);
    }

    record SocketContext(
            Authentication authentication,
            String workspaceId,
            UUID channelId,
            boolean bidirectional,
            String path) {}
}
