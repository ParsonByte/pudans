package cn.tolim.pudans.core.ai.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.CatalogEntry;
import cn.tolim.pudans.contract.ai.dto.AiToolDescriptor;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.catalog.CatalogEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class AiCatalogsTest {

    private static final Instant NOW = Instant.parse("2026-11-25T02:00:00Z");

    @Mock
    CatalogEntryRepository entries;

    @Mock
    AiModelRepository models;

    @Mock
    ObjectProvider<AiCapabilityRegistry> registryProvider;

    private AiCatalogs catalogs;

    @BeforeEach
    void setUp() {
        catalogs = new AiCatalogs(entries, models, registryProvider);
        catalogs.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returnsEmptyCatalogsWhenNothingIsConfiguredOrRegistered() {
        when(entries.findActiveByKind(anyString(), any())).thenReturn(Flux.empty());
        when(models.findAvailable("workspace-a")).thenReturn(Flux.empty());
        when(registryProvider.getIfAvailable()).thenReturn(null);

        var creation = catalogs.creation("workspace-a").block();
        var chat = catalogs.chat("workspace-a").block();

        assertThat(creation).isNotNull();
        assertThat(creation.mediaModes()).isEmpty();
        assertThat(chat).isNotNull();
        assertThat(chat.answerModes()).isEmpty();
        assertThat(chat.models()).isEmpty();
        assertThat(chat.tools()).isEmpty();
        assertThat(catalogs.tools().collectList().block()).isEmpty();
        assertThat(catalogs.skills().collectList().block()).isEmpty();
    }

    @Test
    void readsCreationParametersOnlyFromTheModelSchema() {
        var mode = entry("creation_media_mode", "image", "图片", null, 10, Map.of());
        var operation = entry(
                "creation_operation", "edit", "编辑", "image", 20, Map.of());
        var option = entry(
                "creation_parameter_option",
                "ratio",
                "比例",
                "image",
                30,
                Map.of("options", List.of("1:1", "16:9")));
        when(entries.findActiveByKind(eq("creation_media_mode"), any()))
                .thenReturn(Flux.just(mode));
        when(entries.findActiveByKind(eq("creation_operation"), any()))
                .thenReturn(Flux.just(operation));
        when(entries.findActiveByKind(eq("creation_parameter_option"), any()))
                .thenReturn(Flux.just(option));
        var schema = Map.<String, Object>of(
                "params", List.of(Map.of(
                        "key", "ratio",
                        "type", "enum",
                        "options", List.of("1:1", "16:9"))),
                "limits", Map.of("maxQuantity", 9));
        when(models.findAvailable("workspace-a")).thenReturn(Flux.just(model(schema)));

        var creation = catalogs.creation("workspace-a").block();

        assertThat(creation).isNotNull();
        assertThat(creation.mediaModes()).hasSize(1);
        var media = creation.mediaModes().getFirst();
        assertThat(media.operations()).extracting(item -> item.key()).containsExactly("edit");
        assertThat(media.parameterOptions()).extracting(item -> item.key()).containsExactly("ratio");
        assertThat(media.models()).hasSize(1);
        assertThat(media.models().getFirst().params())
                .extracting(param -> param.get("key"))
                .containsExactly("ratio");
        assertThat(media.models().getFirst().limits())
                .containsEntry("maxQuantity", 9);
    }

    @Test
    void returnsOnlyEnabledRegisteredTools() {
        var registry = new AiCapabilityRegistry() {
            @Override
            public Flux<AiToolDescriptor> tools() {
                return Flux.just(
                        new AiToolDescriptor("enabled", "Enabled", null, true),
                        new AiToolDescriptor("disabled", "Disabled", null, false));
            }
        };
        when(registryProvider.getIfAvailable()).thenReturn(registry);

        var tools = catalogs.tools().collectList().block();

        assertThat(tools).extracting(AiToolDescriptor::key).containsExactly("enabled");
    }

    private static AiModel model(Map<String, Object> schema) {
        return new AiModel(
                UUID.randomUUID(),
                "test-image",
                "Test Image",
                null,
                Map.of("image", Map.of()),
                null,
                null,
                schema,
                "active",
                NOW,
                NOW);
    }

    private static CatalogEntry entry(
            String kind,
            String key,
            String label,
            String parent,
            int order,
            Map<String, Object> metadata) {
        return new CatalogEntry(
                UUID.randomUUID(),
                kind,
                key,
                label,
                null,
                parent,
                order,
                true,
                metadata,
                null,
                null,
                NOW,
                NOW);
    }
}
