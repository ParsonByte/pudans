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

    // 缓存启动时的敏感配置状态，供后续判断。
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

    // 返回最近一次捕获的服务器配置快照。
    public Snapshot current() {
        return snapshot.get();
    }

    public record Snapshot(boolean masterKeyConfigured, boolean hashIdSaltConfigured) {}
}
