package cn.tolim.pudans.core.ai.provider.preset;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import org.junit.jupiter.api.Test;

class ProviderPresetCatalogTest {

    private final ProviderPresetCatalog.Catalog catalog = new ProviderPresetCatalog().catalog();

    @Test
    void containsExactlyTheThreeInternationalAndThreeDomesticProviders() {
        assertThat(catalog.supported())
                .extracting(ProviderPresetCatalog.ProviderPreset::id)
                .containsExactly("openai", "anthropic", "gemini", "deepseek", "minimax", "qianwen");
        assertThat(catalog.unsupported()).isEmpty();
    }

    @Test
    void everyPresetHasAVendorAndResolvableRegions() {
        assertThat(catalog.supported()).allSatisfy(preset -> {
            assertThat(preset.vendor()).isNotNull();
            assertThat(preset.regions()).isNotEmpty();
            assertThat(preset.regions()).allSatisfy(region ->
                    assertThat(region.baseUrl()).startsWith("http"));
        });
    }

    @Test
    void presetsExposeVendorIdentityWithoutProtocolVariants() {
        assertThat(preset("openai").vendor()).isEqualTo(AiProvider.Vendor.OPENAI);
        assertThat(preset("anthropic").vendor()).isEqualTo(AiProvider.Vendor.ANTHROPIC);
        assertThat(preset("gemini").vendor()).isEqualTo(AiProvider.Vendor.GEMINI);
        assertThat(preset("deepseek").vendor()).isEqualTo(AiProvider.Vendor.DEEPSEEK);
        assertThat(preset("minimax").vendor()).isEqualTo(AiProvider.Vendor.MINIMAX);
        assertThat(preset("qianwen").vendor()).isEqualTo(AiProvider.Vendor.QIANWEN);
    }

    private ProviderPresetCatalog.ProviderPreset preset(String id) {
        return catalog.supported().stream()
                .filter(preset -> preset.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}