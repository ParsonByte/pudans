package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.core.ai.provider.AiWebClientConfiguration;
import cn.tolim.pudans.core.infra.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class DeepSeekBalanceClient extends AbstractDeepSeekClient {

    static final String BALANCE_PATH = "/user/balance";

    static final String UPSTREAM_ERROR_CODE = "ai-provider-balance-upstream-error";

    private static final String DEFAULT_ERROR = "DeepSeek 余额查询失败。";

    public DeepSeekBalanceClient(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            ProviderAccounts providerAccounts) {
        super(webClient, mapper, providerAccounts, UPSTREAM_ERROR_CODE, DEFAULT_ERROR);
    }

    public Mono<DeepSeekBalance> get(AiProvider provider, AiProviderAccount account) {
        return Mono.defer(() -> withApiKey(account, apiKey -> webClient()
                                .get()
                                .uri(AiProviderHttpSupport.endpoint(provider, BALANCE_PATH))
                                .headers(headers -> headers.addAll(
                                        headers(provider, apiKey, MediaType.APPLICATION_JSON)))
                                .retrieve()
                                .bodyToMono(String.class)
                                .switchIfEmpty(Mono.error(upstreamError("余额响应为空。", null))))
                        .map(this::parse))
                .onErrorMap(error -> !(error instanceof ApiException), this::upstreamError);
    }

    private DeepSeekBalance parse(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, DeepSeekProtocol.NAME);
        var infos = root.get("balance_infos");
        if (infos != null && !infos.isArray()) {
            throw new AiProtocolException("DeepSeek 余额响应 balance_infos 必须是数组");
        }
        var items = new ArrayList<BalanceInfo>();
        if (infos != null) {
            for (var item : infos) {
                items.add(new BalanceInfo(
                        AiProviderHttpSupport.text(item, "currency"),
                        AiProviderHttpSupport.text(item, "total_balance"),
                        AiProviderHttpSupport.text(item, "granted_balance"),
                        AiProviderHttpSupport.text(item, "topped_up_balance")));
            }
        }
        var available = root.get("is_available");
        return new DeepSeekBalance(
                available != null && available.isBoolean() && available.booleanValue(),
                List.copyOf(items));
    }

    public record DeepSeekBalance(boolean available, List<BalanceInfo> balanceInfos) {}

    public record BalanceInfo(
            String currency,
            String totalBalance,
            String grantedBalance,
            String toppedUpBalance) {}
}
