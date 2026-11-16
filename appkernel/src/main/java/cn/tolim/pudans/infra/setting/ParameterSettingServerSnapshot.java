package cn.tolim.pudans.infra.setting;

import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParameterSettingServerSnapshot {

    private final ParameterSettingGroupService parameterSettingGroupService;

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(new Snapshot(false, false));

    @EventListener
    void onExtensionInitialized(ExtensionInitializedEvent ignored) {
        parameterSettingGroupService.getServer().subscribe(
                server -> {
                    snapshot.set(new Snapshot(
                            StringUtils.hasText(server.getMasterKey()),
                            StringUtils.hasText(server.getHashIdSalt())));
                    log.info(
                            "Parameter server snapshot captured: masterKeyConfigured={}, hashIdSaltConfigured={}",
                            snapshot.get().masterKeyConfigured(),
                            snapshot.get().hashIdSaltConfigured());
                },
                error -> log.warn("Unable to capture the parameter server snapshot", error));
    }

    public Snapshot current() {
        return snapshot.get();
    }

    public record Snapshot(boolean masterKeyConfigured, boolean hashIdSaltConfigured) {}
}
