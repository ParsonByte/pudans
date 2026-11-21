package cn.tolim.pudans.core.ai.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.ai.resource.AiRouteGroup;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RouteCandidatesTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AiStatsRepository statsRepository;

    private SunkRouteRegistry sunkRegistry;

    private RouteCandidates service;

    @BeforeEach
    void setUp() {
        sunkRegistry = new SunkRouteRegistry();
        sunkRegistry.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        when(statsRepository.recentRoutePerformance(any())).thenReturn(Flux.empty());
        service = new RouteCandidates(client, sunkRegistry, statsRepository);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void appliesRecentPerformanceToSeats() {
        var group = group(member("model-a"));
        when(statsRepository.recentRoutePerformance(any())).thenReturn(Flux.just(
                new AiStatsRepository.RoutePerformanceRow(
                        "provider-a", "account-a", "model-a",
                        java.math.BigDecimal.valueOf(320), java.math.BigDecimal.valueOf(42.5), 900L)));
        when(client.get(AiModel.class, "model-a")).thenReturn(Mono.just(model("model-a", "provider-a")));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider("provider-a", true)));
        when(client.listAll(eq(AiProviderAccount.class), any(), any()))
                .thenReturn(Flux.just(account("account-a", 10)));

        StepVerifier.create(service.expand(group))
                .assertNext(seats -> {
                    assertThat(seats).hasSize(1);
                    assertThat(seats.getFirst().getTtftMillis()).isEqualTo(320);
                    assertThat(seats.getFirst().getTps()).isEqualTo(42.5);
                    assertThat(seats.getFirst().getTokensUsed()).isEqualTo(900);
                })
                .verifyComplete();
    }

    @Test
    void expandsAModelAcrossEnabledAccounts() {
        var group = group(member("model-a"));
        when(client.get(AiModel.class, "model-a")).thenReturn(Mono.just(model("model-a", "provider-a")));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider("provider-a", true)));
        when(client.listAll(eq(AiProviderAccount.class), any(), any()))
                .thenReturn(Flux.just(account("account-a", 10), disabledAccount("account-b")));

        StepVerifier.create(service.expand(group))
                .assertNext(seats -> {
                    assertThat(seats).hasSize(1);
                    assertThat(seats.getFirst().getAccount()).isEqualTo("account-a");
                    assertThat(seats.getFirst().getProvider()).isEqualTo("provider-a");
                    assertThat(seats.getFirst().getModel()).isEqualTo("model-a");
                })
                .verifyComplete();
    }

    @Test
    void skipsNonChatModels() {
        var group = group(member("embedding-model"));
        var model = model("embedding-model", "provider-a");
        model.getSpec().setModelType(AiModel.ModelType.EMBEDDING);
        when(client.get(AiModel.class, "embedding-model")).thenReturn(Mono.just(model));

        StepVerifier.create(service.expand(group))
                .assertNext(seats -> assertThat(seats).isEmpty())
                .verifyComplete();
    }

    @Test
    void skipsDisabledProviders() {
        var group = group(member("model-a"));
        when(client.get(AiModel.class, "model-a")).thenReturn(Mono.just(model("model-a", "provider-a")));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider("provider-a", false)));

        StepVerifier.create(service.expand(group))
                .assertNext(seats -> assertThat(seats).isEmpty())
                .verifyComplete();
    }

    @Test
    void marksAnExhaustedAccountAsSpent() {
        var group = group(member("model-a"));
        when(client.get(AiModel.class, "model-a")).thenReturn(Mono.just(model("model-a", "provider-a")));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider("provider-a", true)));
        var exhausted = account("account-a", 10);
        exhausted.getSpec().getQuota().setLimit(100L);
        exhausted.getSpec().getQuota().setUsed(100L);
        when(client.listAll(eq(AiProviderAccount.class), any(), any())).thenReturn(Flux.just(exhausted));

        StepVerifier.create(service.expand(group))
                .assertNext(seats -> assertThat(seats.getFirst().isSpent()).isTrue())
                .verifyComplete();
    }

    @Test
    void computesPaceFromTheQuotaWindow() {
        var group = group(member("model-a"));
        when(client.get(AiModel.class, "model-a")).thenReturn(Mono.just(model("model-a", "provider-a")));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider("provider-a", true)));
        var account = account("account-a", 10);
        account.getSpec().getQuota().setLimit(100L);
        account.getSpec().getQuota().setUsed(40L);
        account.getSpec().getQuota().setResetAt(NOW.plusSeconds(3600));
        when(client.listAll(eq(AiProviderAccount.class), any(), any())).thenReturn(Flux.just(account));

        StepVerifier.create(service.expand(group))
                .assertNext(seats -> assertThat(seats.getFirst().getPace()).isEqualTo(60.0))
                .verifyComplete();
    }

    @Test
    void marksRecentlyRateLimitedAccountsAsSunkUntilTheWindowExpires() {
        var group = group(member("model-a"));
        when(client.get(AiModel.class, "model-a")).thenReturn(Mono.just(model("model-a", "provider-a")));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider("provider-a", true)));
        when(client.listAll(eq(AiProviderAccount.class), any(), any()))
                .thenReturn(Flux.just(account("account-a", 10)));
        sunkRegistry.markSunk("provider-a", "account-a");

        StepVerifier.create(service.expand(group))
                .assertNext(seats -> assertThat(seats.getFirst().isSunk()).isTrue())
                .verifyComplete();

        sunkRegistry.setClock(Clock.fixed(NOW.plusSeconds(61), ZoneOffset.UTC));

        StepVerifier.create(service.expand(group))
                .assertNext(seats -> assertThat(seats.getFirst().isSunk()).isFalse())
                .verifyComplete();
    }

    @Test
    void deduplicatesRepeatedMembers() {
        var group = group(member("model-a"), member("model-a"));
        when(client.get(AiModel.class, "model-a")).thenReturn(Mono.just(model("model-a", "provider-a")));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider("provider-a", true)));
        when(client.listAll(eq(AiProviderAccount.class), any(), any()))
                .thenReturn(Flux.just(account("account-a", 10)));

        StepVerifier.create(service.expand(group))
                .assertNext(seats -> assertThat(seats).hasSize(1))
                .verifyComplete();
    }

    @Test
    void filtersAccountsByTheRouteGroupWhitelist() {
        var group = group(member("model-a"));
        group.getSpec().setAccounts(List.of("account-b"));
        when(client.get(AiModel.class, "model-a")).thenReturn(Mono.just(model("model-a", "provider-a")));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider("provider-a", true)));
        when(client.listAll(eq(AiProviderAccount.class), any(), any()))
                .thenReturn(Flux.just(account("account-a", 0), account("account-b", 1)));

        StepVerifier.create(service.expand(group))
                .assertNext(seats -> {
                    assertThat(seats).hasSize(1);
                    assertThat(seats.getFirst().getAccount()).isEqualTo("account-b");
                })
                .verifyComplete();
    }

    @Test
    void appendsProviderFallbackSeatsAfterThePrimarySeats() {
        var group = group(member("model-a"));
        var primary = provider("provider-a", true);
        primary.getSpec().setOrder(1);
        primary.getSpec().setFallback(List.of("provider-b"));
        var fallback = provider("provider-b", true);
        fallback.getSpec().setOrder(5);
        when(client.get(AiModel.class, "model-a")).thenReturn(Mono.just(model("model-a", "provider-a")));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(primary));
        when(client.get(AiProvider.class, "provider-b")).thenReturn(Mono.just(fallback));
        when(client.listAll(eq(AiProviderAccount.class), any(), any()))
                .thenReturn(Flux.just(account("account-a", 0)))
                .thenReturn(Flux.just(account("account-b", 0)));
        when(client.listAll(eq(AiModel.class), any(), any()))
                .thenReturn(Flux.just(model("model-b", "provider-b")));

        StepVerifier.create(service.expand(group))
                .assertNext(seats -> {
                    assertThat(seats).hasSize(2);
                    assertThat(seats.getFirst().getProvider()).isEqualTo("provider-a");
                    assertThat(seats.getFirst().getProviderOrder()).isEqualTo(1);
                    assertThat(seats.get(1).getProvider()).isEqualTo("provider-b");
                    assertThat(seats.get(1).getAccount()).isEqualTo("account-b");
                    assertThat(seats.get(1).getFallbackSource()).isEqualTo("provider-a");
                    assertThat(seats.get(1).getProviderOrder()).isEqualTo(5);
                })
                .verifyComplete();
    }

    private static AiRouteGroup group(AiRouteGroup.Member... members) {
        var group = new AiRouteGroup();
        group.setMetadata(metadata("group-a"));
        group.getSpec().setDisplayName("路由组 A");
        group.getSpec().getMembers().addAll(java.util.List.of(members));
        return group;
    }

    private static AiRouteGroup.Member member(String model) {
        var member = new AiRouteGroup.Member();
        member.setModel(model);
        return member;
    }

    private static AiModel model(String name, String provider) {
        var model = new AiModel();
        model.setMetadata(metadata(name));
        model.getSpec().setProvider(provider);
        model.getSpec().setModelCode(name);
        model.getSpec().setTier(AiModel.Tier.CHEAP);
        model.getSpec().setContextWindow(128000);
        model.getSpec().setMaxOutputTokens(4096);
        return model;
    }

    private static AiProvider provider(String name, boolean enabled) {
        var provider = new AiProvider();
        provider.setMetadata(metadata(name));
        provider.getSpec().setDisplayName(name);
        provider.getSpec().setProtocol(AiProvider.Protocol.OPENAI_CHAT);
        provider.getSpec().setEnabled(enabled);
        return provider;
    }

    private static AiProviderAccount account(String name, int priority) {
        var account = new AiProviderAccount();
        account.setMetadata(metadata(name));
        account.getSpec().setProvider("provider-a");
        account.getSpec().setDisplayName(name);
        account.getSpec().setPriority(priority);
        return account;
    }

    private static AiProviderAccount disabledAccount(String name) {
        var account = account(name, 0);
        account.getSpec().setEnabled(false);
        return account;
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
