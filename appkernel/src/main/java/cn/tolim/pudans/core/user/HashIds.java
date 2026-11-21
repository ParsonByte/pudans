package cn.tolim.pudans.core.user;

import cn.tolim.pudans.infra.setting.ExtensionInitializedEvent;
import cn.tolim.pudans.infra.setting.ParameterSettingChangedEvent;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class HashIds {

    private static final String DEFAULT_SALT = "pudans";

    private static final int HASH_BYTES = 12;

    private final ParameterGroups parameterGroups;

    private final AtomicReference<String> salt = new AtomicReference<>(DEFAULT_SALT);

    @EventListener
    void onExtensionInitialized(ExtensionInitializedEvent ignored) {
        refreshSalt();
    }

    @EventListener
    void onParameterSettingChanged(ParameterSettingChangedEvent event) {
        if (!Objects.equals(event.getOldData().get("server"), event.getNewData().get("server"))) {
            refreshSalt();
        }
    }

    void refreshSalt() {
        parameterGroups.getServer().subscribe(
                server -> applySalt(server.getHashIdSalt()),
                error -> log.warn("无法读取哈希 ID 盐值，保留之前的盐值", error));
    }

    void applySalt(String value) {
        salt.set(StringUtils.hasText(value) ? value : DEFAULT_SALT);
    }

    public String encode(String value) {
        if (value == null) {
            return null;
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt.get().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            var truncated = new byte[HASH_BYTES];
            System.arraycopy(digest, 0, truncated, 0, HASH_BYTES);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(truncated);
        } catch (Exception e) {
            throw new IllegalStateException("无法生成哈希 ID", e);
        }
    }
}
