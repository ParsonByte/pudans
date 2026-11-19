package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekBalanceClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekProviderAccess;
import java.util.List;
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
    DeepSeekProviderAccess access;

    @Mock
    DeepSeekBalanceClient balanceClient;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        var endpoint = new AiProviderBalanceEndpoint(client, access, balanceClient);
        webClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void queriesAccountBalance() {
        var account = account();
        var provider = provider();
        when(client.get(AiProviderAccount.class, "account-a")).thenReturn(Mono.just(account));
        when(access.requireProviderOf(account)).thenReturn(Mono.just(provider));
        when(balanceClient.get(eq(provider), eq(account)))
                .thenReturn(Mono.just(new DeepSeekBalanceClient.DeepSeekBalance(
                        true,
                        List.of(new DeepSeekBalanceClient.BalanceInfo(
                                "CNY", "110.00", "10.00", "100.00")))));

        webClient.get()
                .uri("/aiprovideraccounts/account-a/balance")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.available").isEqualTo(true)
                .jsonPath("$.balanceInfos[0].currency").isEqualTo("CNY");
    }

    private static AiProvider provider() {
        var provider = new AiProvider();
        provider.setMetadata(metadata("provider-a"));
        provider.getSpec().setDisplayName("DeepSeek");
        provider.getSpec().setProtocol(AiProvider.Protocol.DEEPSEEK);
        provider.getSpec().setBaseUrl("https://api.deepseek.com");
        return provider;
    }

    private static AiProviderAccount account() {
        var account = new AiProviderAccount();
        account.setMetadata(metadata("account-a"));
        account.getSpec().setProvider("provider-a");
        return account;
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
