package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ProviderAccountsTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AiProviderSecretCipher secretCipher;

    @InjectMocks
    ProviderAccounts service;

    @Test
    void decryptsTheStoredApiKey() {
        var account = account("enc:key");
        when(secretCipher.decrypt("enc:key")).thenReturn("plain-key");

        StepVerifier.create(service.apiKey(account))
                .expectNext("plain-key")
                .verifyComplete();
    }

    @Test
    void returnsEmptyForAnAccountWithoutAKey() {
        StepVerifier.create(service.apiKey(account(null)))
                .verifyComplete();
        assertThat(account(null).getSpec().getApiKey()).isNull();
    }

    @Test
    void requireEnabledFiltersDisabledAccounts() {
        var disabled = account("enc:key");
        disabled.getSpec().setEnabled(false);
        when(client.get(AiProviderAccount.class, "account-1")).thenReturn(Mono.just(disabled));

        StepVerifier.create(service.requireEnabled("account-1"))
                .verifyComplete();
    }

    @Test
    void firstEnabledSkipsDisabledAccounts() {
        var disabled = account("enc:key");
        disabled.getSpec().setEnabled(false);
        var enabled = account("enc:key-2");
        when(client.listAll(eq(AiProviderAccount.class), any(), any()))
                .thenReturn(Flux.just(disabled, enabled));

        StepVerifier.create(service.firstEnabled("openai-primary"))
                .expectNext(enabled)
                .verifyComplete();
    }

    private static AiProviderAccount account(String apiKey) {
        var account = new AiProviderAccount();
        var metadata = new Metadata();
        metadata.setName("account-1");
        account.setMetadata(metadata);
        account.getSpec().setProvider("openai-primary");
        account.getSpec().setApiKey(apiKey);
        return account;
    }
}
