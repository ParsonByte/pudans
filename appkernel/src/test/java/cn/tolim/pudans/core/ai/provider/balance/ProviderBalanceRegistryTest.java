package cn.tolim.pudans.core.ai.provider.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.provider.AiProviderBalanceProbeClient;
import cn.tolim.pudans.core.ai.provider.AiProviderBalanceProbeClient.ProbeResult;
import cn.tolim.pudans.infra.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ProviderBalanceRegistryTest {

    @Mock
    ProviderBalanceAdapter first;

    @Mock
    ProviderBalanceAdapter second;

    @Test
    void usesTheFirstSupportingAdapter() {
        var registry = new ProviderBalanceRegistry(List.of(first, second));
        var provider = new AiProvider();
        var account = new AiProviderAccount();
        var result = new ProbeResult("USD", BigDecimal.TEN, Instant.now(), "ok");
        when(first.supports(provider, account)).thenReturn(true);
        when(first.query(provider, account)).thenReturn(Mono.just(result));

        StepVerifier.create(registry.query(provider, account))
                .assertNext(value -> assertThat(value).isEqualTo(result))
                .verifyComplete();

        verify(second, never()).query(provider, account);
    }

    @Test
    void fallsBackToTheNextAdapterWhenTheFirstFails() {
        var registry = new ProviderBalanceRegistry(List.of(first, second));
        var provider = new AiProvider();
        var account = new AiProviderAccount();
        var result = new ProbeResult("CNY", BigDecimal.ONE, Instant.now(), "fallback");
        when(first.supports(provider, account)).thenReturn(true);
        when(second.supports(provider, account)).thenReturn(true);
        when(first.query(provider, account)).thenReturn(Mono.error(new IllegalStateException("probe")));
        when(second.query(provider, account)).thenReturn(Mono.just(result));

        StepVerifier.create(registry.query(provider, account))
                .assertNext(value -> assertThat(value.message()).isEqualTo("fallback"))
                .verifyComplete();
    }

    @Test
    void reportsNotConfiguredWhenNoAdapterSupportsTheAccount() {
        var registry = new ProviderBalanceRegistry(List.of(first, second));
        var provider = new AiProvider();
        var account = new AiProviderAccount();
        when(first.supports(provider, account)).thenReturn(false);
        when(second.supports(provider, account)).thenReturn(false);

        StepVerifier.create(registry.query(provider, account))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo(AiProviderBalanceProbeClient.CODE_NOT_CONFIGURED);
                })
                .verify();
    }
}