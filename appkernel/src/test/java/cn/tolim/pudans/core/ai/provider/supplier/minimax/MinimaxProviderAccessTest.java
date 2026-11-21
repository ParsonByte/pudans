package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.infra.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MinimaxProviderAccessTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ProviderAccounts accountService;

    @InjectMocks
    MinimaxProviderAccess access;

    @Test
    void returnsEnabledMinimaxProvider() {
        var provider = provider(AiProvider.Protocol.MINIMAX);
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider));

        StepVerifier.create(access.requireProvider("provider-a"))
                .expectNext(provider)
                .verifyComplete();
    }

    @Test
    void rejectsNonMinimaxProvider() {
        var provider = provider(AiProvider.Protocol.OPENAI_CHAT);
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider));

        StepVerifier.create(access.requireProvider("provider-a"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, apiError -> {
                            assertThat(apiError.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(apiError.getCode()).isEqualTo(MinimaxProviderAccess.CODE_UNSUPPORTED);
                        }))
                .verify();
    }

    @Test
    void returnsFirstEnabledAccount() {
        var provider = provider(AiProvider.Protocol.MINIMAX);
        var account = new AiProviderAccount();
        account.setMetadata(metadata("account-a"));
        account.getSpec().setProvider("provider-a");
        when(accountService.firstEnabled("provider-a")).thenReturn(Mono.just(account));

        StepVerifier.create(access.requireAccount(provider))
                .expectNext(account)
                .verifyComplete();
    }

    private static AiProvider provider(AiProvider.Protocol protocol) {
        var provider = new AiProvider();
        provider.setMetadata(metadata("provider-a"));
        provider.getSpec().setDisplayName("MiniMax");
        provider.getSpec().setProtocol(protocol);
        provider.getSpec().setBaseUrl("https://api.minimax.cn");
        return provider;
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
