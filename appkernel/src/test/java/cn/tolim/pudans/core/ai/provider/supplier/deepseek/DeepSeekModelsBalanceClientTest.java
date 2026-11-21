package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.account;
import static cn.tolim.pudans.core.ai.provider.AiProviderAdapterTestFixtures.provider;
import static cn.tolim.pudans.core.ai.provider.AiProviderTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class DeepSeekModelsBalanceClientTest {

    private static final String MODELS_BODY = """
            {
              "object": "list",
              "data": [
                {
                  "id": "deepseek-flash",
                  "object": "model",
                  "owned_by": "deepseek",
                  "name": "DeepSeek-V4.1-Flash",
                  "context_window": 1048576,
                  "max_output_tokens": 393216,
                  "input_modalities": ["text", "image"],
                  "output_modalities": ["text"],
                  "effort": {"supported_levels": ["low", "high", "max"], "default_level": "high"},
                  "api_capabilities": {"anthropic_messages": {"system_prompt_update": "in-history"}}
                }
              ]
            }
            """;

    private static final String BALANCE_BODY = """
            {
              "is_available": true,
              "balance_infos": [
                {"currency": "CNY", "total_balance": "110.00",
                 "granted_balance": "10.00", "topped_up_balance": "100.00"}
              ]
            }
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final ProviderAccounts providerAccounts = mock(ProviderAccounts.class);

    @Test
    void listsModels() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        var models = modelsClient(json(MODELS_BODY))
                .list(provider(AiProvider.Protocol.DEEPSEEK), account())
                .block();

        assertThat(models).isNotNull();
        assertThat(models.object()).isEqualTo("list");
        assertThat(models.data()).hasSize(1);
        var model = models.data().get(0);
        assertThat(model.id()).isEqualTo("deepseek-flash");
        assertThat(model.name()).isEqualTo("DeepSeek-V4.1-Flash");
        assertThat(model.contextWindow()).isEqualTo(1048576);
        assertThat(model.maxOutputTokens()).isEqualTo(393216);
        assertThat(model.inputModalities()).containsExactly("text", "image");
        assertThat(model.effort().supportedLevels()).containsExactly("low", "high", "max");
        assertThat(model.effort().defaultLevel()).isEqualTo("high");
        assertThat(model.apiCapabilities().get("anthropic_messages").get("system_prompt_update").asText())
                .isEqualTo("in-history");
    }

    @Test
    void queriesBalance() {
        when(providerAccounts.apiKey(any())).thenReturn(Mono.just("sk-ds-secret"));

        var balance = balanceClient(json(BALANCE_BODY))
                .get(provider(AiProvider.Protocol.DEEPSEEK), account())
                .block();

        assertThat(balance).isNotNull();
        assertThat(balance.available()).isTrue();
        assertThat(balance.balanceInfos()).hasSize(1);
        var info = balance.balanceInfos().get(0);
        assertThat(info.currency()).isEqualTo("CNY");
        assertThat(info.totalBalance()).isEqualTo("110.00");
        assertThat(info.grantedBalance()).isEqualTo("10.00");
        assertThat(info.toppedUpBalance()).isEqualTo("100.00");
    }

    private DeepSeekModelsClient modelsClient(ClientResponse response) {
        return new DeepSeekModelsClient(webClient(response), mapper, providerAccounts);
    }

    private DeepSeekBalanceClient balanceClient(ClientResponse response) {
        return new DeepSeekBalanceClient(webClient(response), mapper, providerAccounts);
    }

    private static WebClient webClient(ClientResponse response) {
        var exchange = (ExchangeFunction) request -> Mono.just(response);
        return WebClient.builder().exchangeFunction(exchange).build();
    }
}
