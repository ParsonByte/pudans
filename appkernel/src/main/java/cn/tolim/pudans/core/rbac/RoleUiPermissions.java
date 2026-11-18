package cn.tolim.pudans.core.rbac;

import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RoleUiPermissions {

    private static final Logger log = LoggerFactory.getLogger(RoleUiPermissions.class);

    private RoleUiPermissions() {}

    public static List<String> of(Role role) {
        var annotations = role.getMetadata() == null ? null : role.getMetadata().getAnnotations();
        if (annotations == null) {
            return List.of();
        }
        var raw = annotations.get(Role.UI_PERMISSIONS_ANNO);
        if (StringUtils.isBlank(raw)) {
            return List.of();
        }
        try {
            List<String> permissions = JsonUtils.jsonToObject(raw, new TypeReference<>() {});
            return permissions == null
                    ? List.of()
                    : permissions.stream().filter(StringUtils::isNotBlank).toList();
        } catch (Exception e) {
            log.warn("无法解析角色 [{}] 的 UI 权限注解", role.getMetadata().getName(), e);
            return List.of();
        }
    }
}
