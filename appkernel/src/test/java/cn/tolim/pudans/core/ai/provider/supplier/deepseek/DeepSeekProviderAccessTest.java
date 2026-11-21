package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.model;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.provider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.infra.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DeepSeekProviderAccessTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ProviderAccounts providerAccounts;

    @InjectMocks
    DeepSeekProviderAccess access;

    @Test
    void returnsEnabledDeepSeekProvider() {
        var provider = provider(AiProvider.Protocol.DEEPSEEK);
        provider.setMetadata(metadata("upstream"));
        when(client.get(AiProvider.class, "upstream")).thenReturn(Mono.just(provider));

        StepVerifier.create(access.requireProvider("upstream"))
                .expectNext(provider)
                .verifyComplete();
    }

    @Test
    void rejectsDisabledProvider() {
        var provider = provider(AiProvider.Protocol.DEEPSEEK);
        provider.getSpec().setEnabled(false);
        when(client.get(AiProvider.class, "upstream")).thenReturn(Mono.just(provider));

        StepVerifier.create(access.requireProvider("upstream"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, apiError -> {
                            assertThat(apiError.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(apiError.getCode()).isEqualTo(DeepSeekProviderAccess.CODE_DISABLED);
                        }))
                .verify();
    }

    @Test
    void rejectsNonDeepSeekProvider() {
        var provider = provider(AiProvider.Protocol.OPENAI_CHAT);
        when(client.get(AiProvider.class, "upstream")).thenReturn(Mono.just(provider));

        StepVerifier.create(access.requireProvider("upstream"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, apiError ->
                                assertThat(apiError.getCode())
                                        .isEqualTo(DeepSeekProviderAccess.CODE_UNSUPPORTED)))
                .verify();
    }

    @Test
    void returnsFirstEnabledAccount() {
        var provider = provider(AiProvider.Protocol.DEEPSEEK);
        provider.setMetadata(metadata("upstream"));
        var account = account();
        when(providerAccounts.firstEnabled("upstream")).thenReturn(Mono.just(account));

        StepVerifier.create(access.requireAccount(provider))
                .expectNext(account)
                .verifyComplete();
    }

    @Test
    void resolvesChatModelOfProvider() {
        var provider = provider(AiProvider.Protocol.DEEPSEEK);
        provider.setMetadata(metadata("upstream"));
        var model = model("deepseek-flash", 128);
        when(client.get(AiModel.class, "flash")).thenReturn(Mono.just(model));

        StepVerifier.create(access.requireChatModel("flash", provider))
                .expectNext(model)
                .verifyComplete();
    }

    @Test
    void rejectsModelOfAnotherProvider() {
        var provider = provider(AiProvider.Protocol.DEEPSEEK);
        provider.setMetadata(metadata("upstream"));
        var model = model("deepseek-flash", 128);
        model.getSpec().setProvider("other");
        when(client.get(AiModel.class, "flash")).thenReturn(Mono.just(model));

        StepVerifier.create(access.requireChatModel("flash", provider))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, apiError ->
                                assertThat(apiError.getCode())
                                        .isEqualTo(DeepSeekProviderAccess.CODE_MODEL_INVALID)))
                .verify();
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
