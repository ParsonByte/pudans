package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.AiProviderModelSyncService.SyncResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiProviderModelSyncSchedulerTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AiProviderModelSyncService modelSyncService;

    @Test
    void syncsEveryEnabledProvider() {
        var first = provider("first", true);
        var second = provider("second", true);
        var disabled = provider("disabled", false);
        when(client.listAll(eq(AiProvider.class), any(), any()))
                .thenReturn(Flux.just(first, second, disabled));
        when(modelSyncService.sync("first")).thenReturn(Mono.just(result()));
        when(modelSyncService.sync("second")).thenReturn(Mono.just(result()));
        var scheduler = new AiProviderModelSyncScheduler(client, modelSyncService);

        StepVerifier.create(scheduler.syncOnce())
                .assertNext(count -> assertThat(count).isEqualTo(2L))
                .verifyComplete();

        verify(modelSyncService).sync("first");
        verify(modelSyncService).sync("second");
    }

    private static AiProvider provider(String name, boolean enabled) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName(name);
        provider.setMetadata(metadata);
        provider.getSpec().setEnabled(enabled);
        return provider;
    }

    private static SyncResult result() {
        return new SyncResult(0, 0, 0, List.of(), List.of());
    }
}