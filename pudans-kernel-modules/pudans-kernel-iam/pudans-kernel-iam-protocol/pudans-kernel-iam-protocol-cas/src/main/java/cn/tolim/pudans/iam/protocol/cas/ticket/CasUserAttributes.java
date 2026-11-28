package cn.tolim.pudans.iam.protocol.cas.ticket;

import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.protocol.cas.util.CasRequestUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;


public final class CasUserAttributes {

    private CasUserAttributes() {
    }

    // Resolve CAS attributes from the user and the configured field mappings.
    public static Map<String, String> resolve(UserDetails user, Map<String, String> mappings) {
        Map<String, String> effective = mappings == null || mappings.isEmpty()
                ? CasRequestUtils.defaultAttributeMappings() : mappings;
        Map<String, String> values = new LinkedHashMap<>();
        effective.forEach((attribute, field) -> {
            String value = resolveField(user, field);
            if (StringUtils.isNotBlank(value)) {
                values.put(attribute, value);
            }
        });
        return values;
    }

    // Resolve a single user field.
    public static String resolveField(UserDetails user, String field) {
        if (user == null || StringUtils.isBlank(field)) {
            return null;
        }
        return switch (field) {
            case "id" -> user.getId();
            case "username" -> user.getUsername();
            case "email" -> user.getEmail();
            case "fullName" -> user.getFullName();
            case "nickName" -> user.getNickName();
            case "phone" -> user.getPhone();
            case "staffNumber" -> user.getStaffNumber();
            case "userType" -> Objects.toString(user.getUserType(), null);
            default -> null;
        };
    }
}