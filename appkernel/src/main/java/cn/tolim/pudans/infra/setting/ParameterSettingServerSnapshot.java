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

    private final ParameterGroups parameterGroups;

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(new Snapshot(false, false));

    @EventListener
    void onExtensionInitialized(ExtensionInitializedEvent ignored) {
        parameterGroups.getServer().subscribe(
                server -> {
                    snapshot.set(new Snapshot(
                            StringUtils.hasText(server.getMasterKey()),
                            StringUtils.hasText(server.getHashIdSalt())));
                    log.info(
                            "已捕获参数服务器快照：masterKeyConfigured={}，hashIdSaltConfigured={}",
                            snapshot.get().masterKeyConfigured(),
                            snapshot.get().hashIdSaltConfigured());
                },
                error -> log.warn("无法捕获参数服务器快照", error));
    }

    public Snapshot current() {
        return snapshot.get();
    }

    public record Snapshot(boolean masterKeyConfigured, boolean hashIdSaltConfigured) {}
}
