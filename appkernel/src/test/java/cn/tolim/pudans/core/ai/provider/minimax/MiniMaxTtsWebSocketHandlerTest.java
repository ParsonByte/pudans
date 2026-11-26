package cn.tolim.pudans.core.ai.provider.minimax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.gateway.AiChannels;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import cn.tolim.pudans.security.authorization.RequestAuthorizer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MiniMaxTtsWebSocketHandlerTest {

    private static final String WORKSPACE = "workspace-a";

    private static final String BASE = "https://api.minimax.cn";

    @Mock
    AiChannels channels;

    @Mock
    AiProviderHttp http;

    @Mock
    RequestAuthorizer authorizer;

    @Mock
    WebSocketClient upstreamClient;

    private UUID channelId;

    private Authentication authentication;

    private MiniMaxTtsWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        channelId = UUID.randomUUID();
        authentication = new UsernamePasswordAuthenticationToken(
                "alice",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_role-template-manage-ai-config")));
        handler = new MiniMaxTtsWebSocketHandler(
                channels,
                new MiniMaxProvider(http),
                authorizer,
                upstreamClient);
    }

    @Test
    void relaysTextFramesToTheProviderWithInjectedCredentials() {
        when(authorizer.allows(any(), any())).thenReturn(Mono.just(true));
        when(channels.resolve(WORKSPACE, channelId)).thenReturn(Mono.just(resolvedChannel()));
        var client = new FakeSession(
                handshake(TTS_PATH),
                Flux.just(message("{\"event\":\"task_start\"}")));
        var upstream = new FakeSession(
                handshake(TTS_PATH),
                Flux.just(message("{\"event\":\"task_started\"}")));
        when(upstreamClient.execute(any(), any(), any())).thenAnswer(invocation -> {
            WebSocketHandler relay = invocation.getArgument(2);
            return relay.handle(upstream);
        });

        StepVerifier.create(handler.handle(client)).verifyComplete();

        assertThat(upstream.texts()).containsExactly("{\"event\":\"task_start\"}");
        assertThat(client.texts()).containsExactly("{\"event\":\"task_started\"}");
        assertThat(client.closed()).isTrue();
        assertThat(upstream.closed()).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpHeaders> headers = ArgumentCaptor.forClass(HttpHeaders.class);
        verify(upstreamClient).execute(
                eq(URI.create("wss://api.minimax.cn/ws/v1/t2a_v2")),
                headers.capture(),
                any());
        assertThat(headers.getValue().getFirst("Authorization")).isEqualTo("Bearer secret");
    }

    @Test
    void usesTheBidirectionalProviderAddress() {
        when(authorizer.allows(any(), any())).thenReturn(Mono.just(true));
        when(channels.resolve(WORKSPACE, channelId)).thenReturn(Mono.just(resolvedChannel()));
        var client = new FakeSession(handshake(BIDI_PATH), Flux.just(message("{\"event\":\"task_start\"}")));
        var upstream = new FakeSession(handshake(BIDI_PATH), Flux.empty());
        when(upstreamClient.execute(any(), any(), any()))
                .thenAnswer(invocation -> ((WebSocketHandler) invocation.getArgument(2))
                        .handle(upstream));

        StepVerifier.create(handler.handle(client)).verifyComplete();

        verify(upstreamClient).execute(
                eq(URI.create("wss://api.minimax.cn/ws/v1/t2a_v2_bidi")),
                any(),
                any());
    }

    @Test
    void rejectsUsersWithoutTheChannelWritePermission() {
        when(authorizer.allows(any(), any())).thenReturn(Mono.just(false));
        var client = new FakeSession(handshake(TTS_PATH), Flux.empty());

        StepVerifier.create(handler.handle(client)).verifyComplete();

        assertThat(client.texts()).hasSize(1);
        assertThat(client.texts().getFirst()).contains("task_failed").contains("1008");
        assertThat(client.status().getCode()).isEqualTo(1008);
        verify(channels, never()).resolve(any(), any());
        verify(upstreamClient, never()).execute(any(), any(), any());
    }

    @Test
    void rejectsHandshakesWithoutAWorkspaceHeader() {
        var client = new FakeSession(handshake(TTS_PATH, new HttpHeaders()), Flux.empty());

        StepVerifier.create(handler.handle(client)).verifyComplete();

        assertThat(client.texts().getFirst()).contains("task_failed");
        assertThat(client.status().getCode()).isEqualTo(1011);
        verify(channels, never()).resolve(any(), any());
    }

    @Test
    void buildsAWriteRequestForTheChannelCapability() {
        var context = new MiniMaxTtsWebSocketHandler.SocketContext(
                authentication,
                WORKSPACE,
                channelId,
                true,
                String.format(TTS_PATH, channelId));

        var request = MiniMaxTtsWebSocketHandler.requestInfo(context);

        assertThat(request.getVerb()).isEqualTo("create");
        assertThat(request.getResource()).isEqualTo("aichannels");
        assertThat(request.getName()).isEqualTo(channelId.toString());
        assertThat(request.getSubresource()).isEqualTo("-");
        assertThat(request.getSubName()).isEqualTo("minimax");
    }

    private static final String TTS_PATH =
            "/apis/api.pudans.tolim.cn/v1alpha1/aichannels/%s/-/minimax/tts-websocket";

    private static final String BIDI_PATH =
            "/apis/api.pudans.tolim.cn/v1alpha1/aichannels/%s/-/minimax/tts-websocket-bidi";

    private HandshakeInfo handshake(String pattern) {
        return handshake(pattern, workspaceHeaders());
    }

    private HandshakeInfo handshake(String pattern, HttpHeaders headers) {
        var path = pattern.replace("%s", channelId.toString());
        return new HandshakeInfo(
                URI.create("https://api.example.com" + path),
                headers,
                Mono.just((Principal) authentication),
                "session-1");
    }

    private static HttpHeaders workspaceHeaders() {
        var headers = new HttpHeaders();
        headers.set(ReactiveRequestIdentity.WORKSPACE_HEADER, WORKSPACE);
        return headers;
    }

    private AiChannels.ResolvedChannel resolvedChannel() {
        var channel = new AiChannelEntity();
        channel.setId(channelId);
        channel.setWorkspaceId(WORKSPACE);
        channel.setProvider("minimax");
        channel.setProtocol("messages");
        channel.setBaseUrl(BASE);
        channel.setTimeoutMs(30_000);
        return new AiChannels.ResolvedChannel(channel, Map.of("apiKey", "secret"));
    }

    private static WebSocketMessage message(String text) {
        return new WebSocketMessage(
                WebSocketMessage.Type.TEXT,
                DefaultDataBufferFactory.sharedInstance.wrap(
                        text.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class FakeSession implements WebSocketSession {

        private final HandshakeInfo handshake;

        private final Flux<WebSocketMessage> inbound;

        private final List<String> texts = new CopyOnWriteArrayList<>();

        private final DataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;

        private volatile boolean open = true;

        private volatile CloseStatus closeStatus;

        private FakeSession(HandshakeInfo handshake, Flux<WebSocketMessage> inbound) {
            this.handshake = handshake;
            this.inbound = inbound;
        }

        @Override
        public String getId() {
            return "fake";
        }

        @Override
        public HandshakeInfo getHandshakeInfo() {
            return handshake;
        }

        @Override
        public DataBufferFactory bufferFactory() {
            return bufferFactory;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

        @Override
        public Flux<WebSocketMessage> receive() {
            return inbound;
        }

        @Override
        public Mono<Void> send(Publisher<WebSocketMessage> messages) {
            return Flux.from(messages)
                    .doOnNext(message -> {
                        if (message.getType() == WebSocketMessage.Type.TEXT) {
                            texts.add(message.getPayloadAsText());
                        }
                        DataBufferUtils.release(message.getPayload());
                    })
                    .then();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public Mono<Void> close() {
            open = false;
            return Mono.empty();
        }

        @Override
        public Mono<Void> close(CloseStatus status) {
            closeStatus = status;
            open = false;
            return Mono.empty();
        }

        @Override
        public Mono<CloseStatus> closeStatus() {
            return Mono.justOrEmpty(closeStatus);
        }

        @Override
        public WebSocketMessage textMessage(String payload) {
            return message(payload);
        }

        @Override
        public WebSocketMessage binaryMessage(Function<DataBufferFactory, DataBuffer> factory) {
            return new WebSocketMessage(WebSocketMessage.Type.BINARY, factory.apply(bufferFactory));
        }

        @Override
        public WebSocketMessage pingMessage(Function<DataBufferFactory, DataBuffer> factory) {
            return new WebSocketMessage(WebSocketMessage.Type.PING, factory.apply(bufferFactory));
        }

        @Override
        public WebSocketMessage pongMessage(Function<DataBufferFactory, DataBuffer> factory) {
            return new WebSocketMessage(WebSocketMessage.Type.PONG, factory.apply(bufferFactory));
        }

        List<String> texts() {
            return texts;
        }

        boolean closed() {
            return !open;
        }

        CloseStatus status() {
            return closeStatus;
        }
    }
}
