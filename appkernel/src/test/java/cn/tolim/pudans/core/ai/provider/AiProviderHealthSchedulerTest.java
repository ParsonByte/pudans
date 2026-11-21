package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.AiProviderBalanceProbeClient.ProbeResult;
import cn.tolim.pudans.core.ai.provider.balance.ProviderBalanceRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiProviderHealthSchedulerTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ProviderBalanceRegistry balanceRegistry;

    @Test
    void probesAccountsAndPersistsBalanceAndHealth() {
        var provider = new AiProvider();
        var providerMetadata = new Metadata();
        providerMetadata.setName("provider-a");
        provider.setMetadata(providerMetadata);

        var account = new AiProviderAccount();
        var accountMetadata = new Metadata();
        accountMetadata.setName("account-a");
        account.setMetadata(accountMetadata);
        account.getSpec().setProvider("provider-a");
        when(client.listAll(eq(AiProviderAccount.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(account));
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider));
        when(balanceRegistry.query(provider, account)).thenReturn(Mono.just(
                new ProbeResult("USD", new BigDecimal("12.50"), Instant.now(), "ok")));
        when(client.update(account)).thenReturn(Mono.just(account));
        var scheduler = new AiProviderHealthScheduler(client, balanceRegistry);

        var probed = scheduler.sweepOnce().block();

        assertThat(probed).isEqualTo(1);
        assertThat(account.getSpec().getBalance().getLeft())
                .isEqualByComparingTo("12.50");
        assertThat(account.getSpec().getBalance().getUnit())
                .isEqualTo(AiProviderAccount.QuotaUnit.USD);
        assertThat(account.getStatus().getHealthScore()).isEqualTo(1.0);
    }
}