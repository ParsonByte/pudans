package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.AiProviderBalanceProbeClient;
import cn.tolim.pudans.core.ai.provider.AiProviderBalanceProbeClient.ProbeResult;
import cn.tolim.pudans.core.ai.provider.balance.ProviderBalanceRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiProviderBalanceEndpointTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ProviderBalanceRegistry balanceRegistry;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        var endpoint = new AiProviderBalanceEndpoint(client, balanceRegistry);
        webClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void returnsTheBalanceResolvedByTheRegistry() {
        var account = account(AiProvider.Protocol.DEEPSEEK);
        when(client.get(AiProviderAccount.class, "account-a")).thenReturn(Mono.just(account));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider(account)));
        when(balanceRegistry.query(eq(provider(account)), eq(account)))
                .thenReturn(Mono.just(new ProbeResult(
                        "CNY", new BigDecimal("110.00"), Instant.parse("2026-11-19T00:00:00Z"), "余额查询成功。")));

        webClient.get()
                .uri("/aiprovideraccounts/account-a/balance")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.unit").isEqualTo("CNY")
                .jsonPath("$.left").isEqualTo(110.00)
                .jsonPath("$.message").isEqualTo("余额查询成功。");
    }

    @Test
    void returnsTheGenericProbeResult() {
        var account = account(AiProvider.Protocol.OPENAI_CHAT);
        when(client.get(AiProviderAccount.class, "account-a")).thenReturn(Mono.just(account));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider(account)));
        when(balanceRegistry.query(eq(provider(account)), eq(account)))
                .thenReturn(Mono.just(new ProbeResult(
                        "USD", BigDecimal.TEN, Instant.parse("2026-11-19T00:00:00Z"), "余额探测成功。")));

        webClient.get()
                .uri("/aiprovideraccounts/account-a/balance")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.unit").isEqualTo("USD")
                .jsonPath("$.left").isEqualTo(10)
                .jsonPath("$.probedAt").isEqualTo("2026-11-19T00:00:00Z")
                .jsonPath("$.message").isEqualTo("余额探测成功。");
    }

    @Test
    void rejectsAnAccountWithoutAnyConfiguredBalanceSource() {
        var account = account(AiProvider.Protocol.OPENAI_CHAT);
        when(client.get(AiProviderAccount.class, "account-a")).thenReturn(Mono.just(account));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider(account)));
        when(balanceRegistry.query(eq(provider(account)), eq(account)))
                .thenReturn(Mono.error(AiProviderBalanceProbeClient.notConfigured()));

        webClient.get()
                .uri("/aiprovideraccounts/account-a/balance")
                .exchange()
                .expectStatus().isBadRequest();
    }

    private static AiProvider provider(AiProviderAccount account) {
        var provider = new AiProvider();
        provider.setMetadata(metadata("provider-a"));
        provider.getSpec().setDisplayName("供应商 A");
        provider.getSpec().setProtocol(account.getSpec().getProtocolOverride() == null
                ? AiProvider.Protocol.DEEPSEEK
                : account.getSpec().getProtocolOverride());
        provider.getSpec().setBaseUrl("https://api.example.com");
        return provider;
    }

    private static AiProviderAccount account(AiProvider.Protocol protocol) {
        var account = new AiProviderAccount();
        account.setMetadata(metadata("account-a"));
        account.getSpec().setProvider("provider-a");
        account.getSpec().setProtocolOverride(protocol);
        return account;
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}