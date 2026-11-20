package cn.tolim.pudans.core.ai.config;

import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiSettingService {

    public static final String SETTING_NAME = "ai-setting";

    private final ReactiveExtensionClient client;

    public Mono<AiSetting> get() {
        return client.get(AiSetting.class, SETTING_NAME);
    }

    public Mono<AiSetting.Spec> spec() {
        return get().map(setting -> setting.getSpec() == null ? new AiSetting.Spec() : setting.getSpec());
    }

    public Mono<AiSetting.Defaults> defaults() {
        return spec().map(spec -> spec.getDefaults() == null ? new AiSetting.Defaults() : spec.getDefaults());
    }

    public Mono<Boolean> memoryEnabled() {
        return spec()
                .map(spec -> spec.getMemory() == null || spec.getMemory().isEnabled())
                .defaultIfEmpty(true);
    }
}
