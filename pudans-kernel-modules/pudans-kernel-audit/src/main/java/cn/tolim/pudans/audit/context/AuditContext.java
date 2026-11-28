package cn.tolim.pudans.audit.context;

import cn.tolim.pudans.audit.entity.Target;
import org.springframework.security.core.Authentication;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AuditContext {

    private static final ThreadLocal<String> CONTENT = new ThreadLocal<>();

    private static final ThreadLocal<Authentication> AUTHENTICATION = new ThreadLocal<>();

    private static final ThreadLocal<List<Target>> TARGET_LIST = new ThreadLocal<>();

    private static final ThreadLocal<Map<String, Object>> ADDITIONAL_DATA = new ThreadLocal<>();

    private AuditContext() {
    }

    public static String getContent() {
        return CONTENT.get();
    }

    public static void setContent(String content) {
        CONTENT.set(content);
    }

    public static void removeContent() {
        CONTENT.remove();
    }

    public static Object getAdditionalData(String key) {
        return getAdditionalData().get(key);
    }

    public static Map<String, Object> getAdditionalData() {
        Map<String, Object> values = ADDITIONAL_DATA.get();
        if (CollectionUtils.isEmpty(values)) {
            values = new HashMap<>(16);
            ADDITIONAL_DATA.set(values);
        }
        return values;
    }

    public static void putAdditionalData(String key, Object value) {
        getAdditionalData().put(key, value);
    }

    public static void putAdditionalData(Map<String, Object> value) {
        ADDITIONAL_DATA.set(value);
    }

    public static Authentication getAuthorization() {
        return AUTHENTICATION.get();
    }

    public static void setAuthorization(Authentication authorization) {
        AUTHENTICATION.set(authorization);
    }

    public static List<Target> getTarget() {
        return TARGET_LIST.get();
    }

    public static void setTarget(Target... target) {
        if (target != null) {
            TARGET_LIST.set(List.of(target));
        }
    }

    public static void setTarget(List<Target> targets) {
        if (!CollectionUtils.isEmpty(targets)) {
            TARGET_LIST.set(targets);
        }
    }

    public static void addTarget(Target target) {
        if (Objects.nonNull(target)) {
            List<Target> targets = TARGET_LIST.get();
            if (targets == null) {
                targets = new ArrayList<>();
            }
            targets.add(target);
            TARGET_LIST.set(targets);
        }
    }

    public static void removeTarget() {
        TARGET_LIST.remove();
    }

    public static void removeAuthentication() {
        AUTHENTICATION.remove();
    }

    public static void removeAdditionalData() {
        ADDITIONAL_DATA.remove();
    }

    public static void removeAdditionalData(String key) {
        Map<String, Object> values = ADDITIONAL_DATA.get();
        if (!CollectionUtils.isEmpty(values)) {
            values.remove(key);
        }
    }

    public static void removeAuditContext() {
        removeAdditionalData();
        removeContent();
        removeTarget();
        removeAuthentication();
    }

    public static void clear() {
        removeAuditContext();
    }
}