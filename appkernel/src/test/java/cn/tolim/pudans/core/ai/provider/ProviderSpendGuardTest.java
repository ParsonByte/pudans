package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository;
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
class ProviderSpendGuardTest {

    @Mock
    AiStatsRepository statsRepository;

    @Test
    void rejectsAnExhaustedQuotaWindow() {
        var account = account();
        var window = new AiProviderAccount.QuotaWindow();
        window.setName("hourly");
        window.setLimit(100L);
        window.setUsed(100L);
        window.setResetsAt(Instant.now().plusSeconds(3600));
        account.getSpec().setWindows(List.of(window));
        var guard = new ProviderSpendGuard(statsRepository);

        StepVerifier.create(guard.check(provider(), account, BigDecimal.ONE))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasMessageContaining("配额窗口"))
                .verify();
    }

    @Test
    void rejectsSpendingAboveTheAccountCap() {
        var account = account();
        account.getSpec().setSpendCapPoints(100L);
        when(statsRepository.providerAccountSpent("provider-a", "account-a"))
                .thenReturn(Mono.just(BigDecimal.valueOf(95)));
        var guard = new ProviderSpendGuard(statsRepository);

        StepVerifier.create(guard.check(provider(), account, BigDecimal.TEN))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasMessageContaining("累计花费"))
                .verify();
    }

    @Test
    void allowsRequestsWithinTheCap() {
        var account = account();
        account.getSpec().setSpendCapPoints(100L);
        when(statsRepository.providerAccountSpent("provider-a", "account-a"))
                .thenReturn(Mono.just(BigDecimal.valueOf(50)));
        var guard = new ProviderSpendGuard(statsRepository);

        StepVerifier.create(guard.check(provider(), account, BigDecimal.TEN)).verifyComplete();
    }

    private static AiProvider provider() {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("provider-a");
        provider.setMetadata(metadata);
        return provider;
    }

    private static AiProviderAccount account() {
        var account = new AiProviderAccount();
        var metadata = new Metadata();
        metadata.setName("account-a");
        account.setMetadata(metadata);
        return account;
    }
}