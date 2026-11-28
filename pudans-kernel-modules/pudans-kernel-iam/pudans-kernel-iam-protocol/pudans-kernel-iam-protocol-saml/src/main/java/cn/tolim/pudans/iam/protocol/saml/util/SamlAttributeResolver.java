package cn.tolim.pudans.iam.protocol.saml.util;

import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


public final class SamlAttributeResolver {

    private SamlAttributeResolver() {
    }

    // Resolve assertion attributes from the user and the configured field mappings.
    public static Map<String, String> resolve(UserDetails user, Map<String, String> mappings) {
        Map<String, String> effective = mappings == null || mappings.isEmpty()
                ? defaultMappings() : mappings;
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
            case "groups" -> join(user.getGroups());
            case "organizations" -> join(user.getOrganizations());
            default -> null;
        };
    }

    // Default attribute names.
    public static Map<String, String> defaultMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("id", "id");
        mappings.put("username", "username");
        mappings.put("email", "email");
        mappings.put("fullName", "fullName");
        mappings.put("phone", "phone");
        return mappings;
    }

    private static String join(Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(Objects::toString)
                .collect(Collectors.joining(","));
    }
}