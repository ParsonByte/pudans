package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.provider.AiProviderBalanceProbeClient.ProbeResult;
import cn.tolim.pudans.core.ai.provider.balance.ProviderBalanceAdapter;
import cn.tolim.pudans.infra.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@Order(100)
@RequiredArgsConstructor
public class DeepSeekBalanceAdapter implements ProviderBalanceAdapter {

    private final DeepSeekBalanceClient balanceClient;

    @Override
    public boolean supports(AiProvider provider, AiProviderAccount account) {
        return DeepSeekProtocol.isDeepSeek(protocolOf(provider, account));
    }

    @Override
    public Mono<ProbeResult> query(AiProvider provider, AiProviderAccount account) {
        return balanceClient.get(provider, account).map(balance -> {
            var info = balance.balanceInfos() == null || balance.balanceInfos().isEmpty()
                    ? null
                    : balance.balanceInfos().getFirst();
            var left = amount(info == null ? null : info.totalBalance());
            if (left == null) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        DeepSeekBalanceClient.UPSTREAM_ERROR_CODE,
                        "DeepSeek 余额响应缺少数值字段。");
            }
            return new ProbeResult(
                    info.currency(),
                    left,
                    Instant.now(),
                    balance.available() ? "DeepSeek 余额查询成功。" : "DeepSeek 余额不可用。");
        });
    }

    private static AiProvider.Protocol protocolOf(AiProvider provider, AiProviderAccount account) {
        var override = account.getSpec() == null ? null : account.getSpec().getProtocolOverride();
        return override == null ? provider.getSpec().getProtocol() : override;
    }

    private static BigDecimal amount(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException error) {
            return null;
        }
    }
}