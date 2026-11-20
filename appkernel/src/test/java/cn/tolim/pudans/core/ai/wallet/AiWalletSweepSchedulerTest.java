package cn.tolim.pudans.core.ai.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiWalletSweepSchedulerTest {

    @Mock
    AiWalletService walletService;

    @Test
    void sweepsExpiredHoldsOnEachTick() {
        when(walletService.sweepExpired())
                .thenReturn(Mono.just(new AiWalletService.SweepResult(2)));
        var scheduler = new AiWalletSweepScheduler(walletService);

        StepVerifier.create(scheduler.sweepOnce())
                .assertNext(result -> assertThat(result.releasedCount()).isEqualTo(2))
                .verifyComplete();

        verify(walletService).sweepExpired();
    }
}