package cn.tolim.pudans.core.ai.task;

import cn.tolim.pudans.core.ai.wallet.AiWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class WalletSweepTaskHandler implements AiTaskHandler {

    public static final String TASK_TYPE = "WALLET_SWEEP";

    private final AiWalletService walletService;

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public Mono<Void> handle(AiTask task) {
        return walletService.sweepExpired().then();
    }
}
