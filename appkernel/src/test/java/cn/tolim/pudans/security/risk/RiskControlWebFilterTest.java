package cn.tolim.pudans.security.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import cn.tolim.pudans.contract.core.extension.audit.AuditRecord;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.risk.service.RiskControlService;
import cn.tolim.pudans.core.risk.service.RiskDecision;
import cn.tolim.pudans.core.risk.service.RiskRuleRegistry;
import cn.tolim.pudans.core.risk.service.RiskScene;
import cn.tolim.pudans.infra.feishu.FeishuAlertNotifier;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class RiskControlWebFilterTest {

    @Mock
    RiskControlService riskControlService;

    @Mock
    RiskRuleRegistry riskRuleRegistry;

    @Mock
    ServerSecurityContextRepository securityContextRepository;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AuditService auditService;

    @Mock
    FeishuAlertNotifier alertNotifier;

    @Mock
    DomainEventPublisher domainEventPublisher;

    @Mock
    ServerResponse.Context context;

    private RiskControlWebFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RiskControlWebFilter(
                riskControlService,
                riskRuleRegistry,
                securityContextRepository,
                client,
                auditService,
                alertNotifier,
                domainEventPublisher,
                context);
        when(context.messageWriters()).thenReturn(ServerCodecConfigurer.create().getWriters());
        when(context.viewResolvers()).thenReturn(List.of());
        when(securityContextRepository.load(any())).thenReturn(Mono.empty());
        when(alertNotifier.send(any())).thenReturn(Mono.empty());
        when(riskRuleRegistry.enabledScenes()).thenReturn(java.util.Set.of(RiskScene.AUTH_FAILURE));
        when(auditService.newRecord(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            var record = new AuditRecord();
            var metadata = new Metadata();
            metadata.setName("audit-" + System.nanoTime());
            record.setMetadata(metadata);
            record.setUid(invocation.getArgument(1));
            return record;
        });
    }

    private static MockServerWebExchange exchange() {
        try {
            return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users")
                    .remoteAddress(new InetSocketAddress(InetAddress.getByName("8.8.8.8"), 12345))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static MockServerWebExchange pageExchange() {
        try {
            return MockServerWebExchange.from(MockServerHttpRequest.get("/sign-in")
                    .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                    .remoteAddress(new InetSocketAddress(InetAddress.getByName("8.8.8.8"), 12345))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void bannedRequestIsRejectedWith429AndNeverReachesTheChain() {
        when(riskControlService.firstBannedScene(any(Collection.class), anyString()))
                .thenReturn(Mono.just(RiskScene.REQUEST_FLOOD));
        when(riskControlService.markAlerted(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(false));
        var exchange = exchange();
        var chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = ignored -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(chainInvoked).isFalse();
        verify(riskControlService, never()).ban(anyString(), anyString(), any(Duration.class));
        verify(auditService).record(any(AuditRecord.class));
    }

    @Test
    void bannedPageNavigationRedirectsToTheRateLimitPage() {
        when(riskControlService.firstBannedScene(any(Collection.class), anyString()))
                .thenReturn(Mono.just(RiskScene.REQUEST_FLOOD));
        when(riskControlService.markAlerted(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(false));
        var exchange = pageExchange();
        var chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = ignored -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
        var location = exchange.getResponse().getHeaders().getLocation();
        assertThat(location).isNotNull();
        assertThat(location.getPath()).isEqualTo("/429");
        assertThat(location.getQuery()).contains("code=risk-blocked");
        assertThat(chainInvoked).isFalse();
    }

    @Test
    void activeBanIsCheckedAgainstEveryEnabledScene() {
        when(riskControlService.firstBannedScene(any(Collection.class), anyString()))
                .thenReturn(Mono.empty());
        when(riskControlService.evaluate(RiskScene.REQUEST_FLOOD, "8.8.8.8"))
                .thenReturn(Mono.just(RiskDecision.permit()));
        var exchange = exchange();
        var chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = current -> {
            chainInvoked.set(true);
            current.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chainInvoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void staticAssetsAreNotRateLimited() {
        for (var path : List.of("/webjars/index.js", "/assets/roles-DYFfexQq.js", "/brand/logo-dark.svg")) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());

            StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();
        }

        verify(riskControlService, never()).firstBannedScene(any(Collection.class), anyString());
    }

    @Test
    void rateLimitPageIsReachableWhileBanned() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/429").build());

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();

        verify(riskControlService, never()).firstBannedScene(any(Collection.class), anyString());
    }

    @Test
    void superAdminRequestsBypassRiskControl() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_super-role")));
        when(securityContextRepository.load(any()))
                .thenReturn(Mono.just(new SecurityContextImpl(authentication)));
        var exchange = exchange();
        var chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = current -> {
            chainInvoked.set(true);
            current.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chainInvoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(riskControlService, never()).firstBannedScene(any(Collection.class), anyString());
        verify(riskControlService, never()).evaluate(anyString(), anyString());
    }

    @Test
    void blockedAuthenticatedRequestRecordsTheTriggeringUser() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_normal-role")));
        when(securityContextRepository.load(any()))
                .thenReturn(Mono.just(new SecurityContextImpl(authentication)));
        var user = new User();
        var spec = new User.UserSpec();
        spec.setUid("uid-1");
        user.setSpec(spec);
        when(client.fetch(User.class, "alice")).thenReturn(Mono.just(user));
        when(riskControlService.firstBannedScene(any(Collection.class), anyString()))
                .thenReturn(Mono.just(RiskScene.REQUEST_FLOOD));
        when(riskControlService.markAlerted(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(false));
        var exchange = exchange();

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();

        var captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().getUid()).isEqualTo("uid-1");
        assertThat(captor.getValue().getContext()).containsEntry("username", "alice");
    }
}
