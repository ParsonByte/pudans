package cn.tolim.pudans.core.rbac.service;

import cn.tolim.pudans.contract.infra.SystemSetting.ExtensionPointEnabled;
import cn.tolim.pudans.contract.plugin.SpringPluginManager;
import cn.tolim.pudans.contract.plugin.extensionpoint.ExtensionGetter;
import cn.tolim.pudans.contract.security.RbacRuleEnricher;
import cn.tolim.pudans.infra.setting.SystemConfigChangedEvent;
import cn.tolim.pudans.plugin.event.PudansPluginStartedEvent;
import cn.tolim.pudans.plugin.event.PudansPluginStoppedEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class RbacEnricherSignature implements InitializingBean, DisposableBean {

    public static final String EMPTY = "none";

    private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(30);

    private final ExtensionGetter extensionGetter;

    private final SpringPluginManager pluginManager;

    private volatile String signature = EMPTY;

    private Disposable ticker;

    private static String changedValue(Map<String, String> data) {
        return data == null ? null : data.get(ExtensionPointEnabled.GROUP);
    }

    private static String digest(String raw) throws Exception {
        var bytes = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes).substring(0, 12);
    }

    @Override
    public void afterPropertiesSet() {
        refresh();
        ticker = Flux.interval(REFRESH_INTERVAL)
                .publishOn(Schedulers.boundedElastic())
                .subscribe(ignored -> refresh());
    }

    @Override
    public void destroy() {
        var current = ticker;
        if (current != null) {
            current.dispose();
        }
    }

    public String signature() {
        return signature;
    }

    @EventListener
    void onSystemConfigChanged(SystemConfigChangedEvent event) {
        if (!Objects.equals(
                changedValue(event.getOldData()), changedValue(event.getNewData()))) {
            refreshAsync();
        }
    }

    @EventListener
    void onPluginStarted(PudansPluginStartedEvent event) {
        refreshAsync();
    }

    @EventListener
    void onPluginStopped(PudansPluginStoppedEvent event) {
        refreshAsync();
    }

    private void refreshAsync() {
        Schedulers.boundedElastic().schedule(this::refresh);
    }

    void refresh() {
        try {
            var enrichers = extensionGetter.getExtensionList(RbacRuleEnricher.class).stream()
                    .map(enricher -> enricher.getClass().getName())
                    .sorted()
                    .toList();
            if (enrichers.isEmpty()) {
                signature = EMPTY;
                return;
            }
            var plugins = pluginManager.getPlugins().stream()
                    .map(plugin -> plugin.getPluginId() + ":" + plugin.getDescriptor().getVersion())
                    .sorted()
                    .toList();
            signature = digest(String.join(",", enrichers) + "|" + String.join(",", plugins));
        } catch (Throwable error) {
            log.warn("Unable to refresh the RBAC enricher signature", error);
        }
    }
}
