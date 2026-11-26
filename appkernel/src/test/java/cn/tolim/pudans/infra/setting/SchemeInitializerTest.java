package cn.tolim.pudans.infra.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.contract.storage.StorageRoute;
import cn.tolim.pudans.extension.DefaultSchemeManager;
import cn.tolim.pudans.extension.contract.index.ValueIndexSpec;
import cn.tolim.pudans.extension.index.IndexEngine;
import cn.tolim.pudans.extension.index.IndicesManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class SchemeInitializerTest {

    @Test
    @SuppressWarnings("unchecked")
    void registersTheStorageRouteSchemeAndItsPolicyNameIndex() {
        var indexEngine = mock(IndexEngine.class);
        var indicesManager = mock(IndicesManager.class);
        when(indexEngine.getIndicesManager()).thenReturn(indicesManager);
        var schemeManager = new DefaultSchemeManager(indexEngine, mock(ApplicationEventPublisher.class));

        new SchemeInitializer(schemeManager).start();

        assertThatCode(() -> schemeManager.get(StorageRoute.class)).doesNotThrowAnyException();
        ArgumentCaptor<List<ValueIndexSpec<StorageRoute, ?>>> specs = ArgumentCaptor.forClass(List.class);
        verify(indicesManager).add(eq(StorageRoute.class), specs.capture());
        assertThat(specs.getValue().stream().map(ValueIndexSpec::getName)).contains("spec.policyName");
    }

    @Test
    @SuppressWarnings("unchecked")
    void registersTheStoragePolicyIndexesForLiveResources() {
        var indexEngine = mock(IndexEngine.class);
        var indicesManager = mock(IndicesManager.class);
        when(indexEngine.getIndicesManager()).thenReturn(indicesManager);
        var schemeManager = new DefaultSchemeManager(indexEngine, mock(ApplicationEventPublisher.class));

        new SchemeInitializer(schemeManager).start();

        ArgumentCaptor<List<ValueIndexSpec<LiveSource, ?>>> sourceSpecs = ArgumentCaptor.forClass(List.class);
        verify(indicesManager).add(eq(LiveSource.class), sourceSpecs.capture());
        assertThat(sourceSpecs.getValue().stream().map(ValueIndexSpec::getName)).contains("spec.storagePolicy");
        ArgumentCaptor<List<ValueIndexSpec<LiveRecording, ?>>> recordingSpecs = ArgumentCaptor.forClass(List.class);
        verify(indicesManager).add(eq(LiveRecording.class), recordingSpecs.capture());
        assertThat(recordingSpecs.getValue().stream().map(ValueIndexSpec::getName)).contains("spec.storagePolicy");
    }
}