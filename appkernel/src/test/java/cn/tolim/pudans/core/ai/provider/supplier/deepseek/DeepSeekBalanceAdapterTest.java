package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DeepSeekBalanceAdapterTest {

    @Mock
    DeepSeekBalanceClient balanceClient;

    @InjectMocks
    DeepSeekBalanceAdapter adapter;

    @Test
    void supportsDeepSeekProtocolsAndOverrides() {
        var provider = provider(AiProvider.Protocol.OPENAI_CHAT);
        var account = new AiProviderAccount();
        account.getSpec().setProtocolOverride(AiProvider.Protocol.DEEPSEEK);

        assertThat(adapter.supports(provider, account)).isTrue();
        assertThat(adapter.supports(provider(AiProvider.Protocol.ANTHROPIC), new AiProviderAccount()))
                .isFalse();
    }

    @Test
    void mapsTheFirstBalanceInfo() {
        var provider = provider(AiProvider.Protocol.DEEPSEEK);
        var account = new AiProviderAccount();
        when(balanceClient.get(provider, account))
                .thenReturn(Mono.just(new DeepSeekBalanceClient.DeepSeekBalance(
                        true,
                        List.of(new DeepSeekBalanceClient.BalanceInfo(
                                "CNY", "110.00", "10.00", "100.00")))));

        StepVerifier.create(adapter.query(provider, account))
                .assertNext(result -> {
                    assertThat(result.unit()).isEqualTo("CNY");
                    assertThat(result.left()).isEqualByComparingTo("110.00");
                    assertThat(result.message()).isEqualTo("DeepSeek 余额查询成功。");
                })
                .verifyComplete();
    }

    @Test
    void rejectsAnEmptyBalancePayload() {
        var provider = provider(AiProvider.Protocol.DEEPSEEK);
        var account = new AiProviderAccount();
        when(balanceClient.get(provider, account))
                .thenReturn(Mono.just(new DeepSeekBalanceClient.DeepSeekBalance(true, List.of())));

        StepVerifier.create(adapter.query(provider, account))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo(DeepSeekBalanceClient.UPSTREAM_ERROR_CODE);
                })
                .verify();
    }

    private static AiProvider provider(AiProvider.Protocol protocol) {
        var provider = new AiProvider();
        provider.getSpec().setProtocol(protocol);
        return provider;
    }
}