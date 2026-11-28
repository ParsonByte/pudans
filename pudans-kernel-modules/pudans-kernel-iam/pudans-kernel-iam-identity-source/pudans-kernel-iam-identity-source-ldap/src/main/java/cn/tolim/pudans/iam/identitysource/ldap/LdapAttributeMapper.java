package cn.tolim.pudans.iam.identitysource.ldap;

import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import cn.tolim.pudans.iam.identitysource.core.domain.UserDetail;
import cn.tolim.pudans.iam.identitysource.ldap.client.LdapEntry;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.util.ArrayList;
import java.util.List;


public class LdapAttributeMapper {

    private final LdapConfig config;

    public LdapAttributeMapper(LdapConfig config) {
        this.config = config;
    }

    // Map an LDAP user entry to the identity source user model
    public User toUser(LdapEntry entry) {
        Attributes attributes = entry.attributes();
        String userId = firstValue(attributes, config.getUserIdAttribute());
        if (StringUtils.isBlank(userId)) {
            userId = StringUtils.defaultIfBlank(firstValue(attributes, config.getUserNameAttribute()),
                    entry.dn());
        }
        String name = firstValue(attributes, config.getUserNameAttribute());
        String displayName = firstValue(attributes, config.getUserDisplayNameAttribute());
        User user = new User();
        user.setUserId(userId);
        user.setEmail(blankToNull(firstValue(attributes, config.getUserEmailAttribute())));
        user.setOrgEmail(user.getEmail());
        user.setPhone(blankToNull(firstValue(attributes, config.getUserPhoneAttribute())));
        if (StringUtils.isNotBlank(config.getPhoneAreaCode())) {
            user.setPhoneAreaCode(config.getPhoneAreaCode());
        }
        if (StringUtils.isNotBlank(config.getUserAvatarAttribute())) {
            user.setAvatar(blankToNull(firstValue(attributes, config.getUserAvatarAttribute())));
        }
        user.setActive(resolveActive(attributes));
        user.setDeptIdList(readMemberOf(attributes));
        UserDetail userDetail = new UserDetail();
        userDetail.setName(StringUtils.defaultIfBlank(name, displayName));
        userDetail.setNickName(StringUtils.defaultIfBlank(displayName, name));
        user.setUserDetail(userDetail);
        return user;
    }

    // Map an LDAP group entry to the identity source department model
    public Dept toDept(LdapEntry entry) {
        Dept dept = new Dept();
        dept.setDeptId(entry.dn());
        dept.setName(StringUtils.defaultIfBlank(
                firstValue(entry.attributes(), config.getGroupNameAttribute()), entry.dn()));
        dept.setOrder(0L);
        return dept;
    }

    // Read the DNs of the groups a user belongs to
    public List<String> getGroupDns(LdapEntry entry) {
        if (StringUtils.isBlank(config.getMemberOfAttribute())) {
            return List.of();
        }
        return values(entry.attributes(), config.getMemberOfAttribute());
    }

    // Read the DNs of the members of a group
    public List<String> getMemberDns(LdapEntry entry) {
        return values(entry.attributes(), config.getGroupMemberAttribute());
    }

    // Read all values of an attribute
    public static List<String> values(Attributes attributes, String name) {
        List<String> values = new ArrayList<>();
        if (attributes == null || StringUtils.isBlank(name)) {
            return values;
        }
        Attribute attribute = attributes.get(name);
        if (attribute == null) {
            return values;
        }
        try {
            NamingEnumeration<?> enumeration = attribute.getAll();
            while (enumeration.hasMore()) {
                Object value = enumeration.next();
                if (value != null) {
                    values.add(String.valueOf(value));
                }
            }
            enumeration.close();
        } catch (NamingException exception) {
            return values;
        }
        return values;
    }

    // Read the first value of an attribute
    public static String firstValue(Attributes attributes, String name) {
        List<String> values = values(attributes, name);
        return values.isEmpty() ? null : values.getFirst();
    }

    // Whether the entry is a group entry
    public static boolean isGroup(Attributes attributes) {
        List<String> objectClasses = values(attributes, "objectClass");
        return objectClasses.stream().anyMatch(objectClass ->
                StringUtils.equalsIgnoreCase(objectClass, "groupOfNames")
                        || StringUtils.equalsIgnoreCase(objectClass, "groupOfUniqueNames")
                        || StringUtils.equalsIgnoreCase(objectClass, "posixGroup")
                        || StringUtils.equalsIgnoreCase(objectClass, "group"));
    }

    // Resolve the user enabled flag from the configured attribute
    private Boolean resolveActive(Attributes attributes) {
        if (StringUtils.isBlank(config.getUserActiveAttribute())) {
            return Boolean.TRUE;
        }
        String value = firstValue(attributes, config.getUserActiveAttribute());
        if (StringUtils.isBlank(value)) {
            return Boolean.TRUE;
        }
        String normalized = value.trim().toLowerCase();
        if (List.of("false", "0", "disabled", "inactive", "locked", "no").contains(normalized)) {
            return Boolean.FALSE;
        }
        return BooleanUtils.toBooleanObject(normalized) != Boolean.FALSE;
    }

    // Read the group DNs from the memberOf attribute
    private List<String> readMemberOf(Attributes attributes) {
        if (StringUtils.isBlank(config.getMemberOfAttribute())) {
            return new ArrayList<>();
        }
        return values(attributes, config.getMemberOfAttribute());
    }

    // Convert blank values to null so database unique indexes behave consistently
    private String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }
}