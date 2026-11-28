package cn.tolim.pudans.iam.identitysource.scim.mapper;

import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import cn.tolim.pudans.iam.identitysource.core.domain.UserDetail;
import cn.tolim.pudans.iam.identitysource.scim.ScimConfig;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimGroup;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimGroupRef;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimMultiValue;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimUser;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;


public class ScimMapper {

    private final ScimConfig config;

    public ScimMapper(ScimConfig config) {
        this.config = config;
    }

    // Map a SCIM user to the identity source user model
    public User toUser(ScimUser scimUser) {
        User user = new User();
        user.setUserId(StringUtils.defaultIfBlank(scimUser.getId(), scimUser.getUserName()));
        user.setEmail(blankToNull(primaryValue(scimUser.getEmails())));
        user.setOrgEmail(user.getEmail());
        user.setPhone(blankToNull(primaryValue(scimUser.getPhoneNumbers())));
        if (StringUtils.isNotBlank(config.getPhoneAreaCode())) {
            user.setPhoneAreaCode(config.getPhoneAreaCode());
        }
        user.setAvatar(blankToNull(primaryValue(scimUser.getPhotos())));
        user.setActive(scimUser.getActive() == null || scimUser.getActive());
        user.setDeptIdList(groupIds(scimUser.getGroups()));
        UserDetail userDetail = new UserDetail();
        userDetail.setName(resolveName(scimUser));
        userDetail.setNickName(resolveNickName(scimUser));
        user.setUserDetail(userDetail);
        return user;
    }

    // Map a SCIM group to the identity source department model
    public Dept toDept(ScimGroup group) {
        Dept dept = new Dept();
        dept.setDeptId(group.getId());
        dept.setName(StringUtils.defaultIfBlank(group.getDisplayName(), group.getId()));
        dept.setOrder(0L);
        return dept;
    }

    // Pick the primary value of a multi-valued attribute, falling back to the first value
    private static String primaryValue(List<ScimMultiValue> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .filter(value -> Boolean.TRUE.equals(value.getPrimary()))
                .map(ScimMultiValue::getValue)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElseGet(() -> values.stream()
                        .map(ScimMultiValue::getValue)
                        .filter(StringUtils::isNotBlank)
                        .findFirst()
                        .orElse(null));
    }

    // Resolve the display name of a SCIM user
    private static String resolveName(ScimUser scimUser) {
        if (scimUser.getName() != null
                && StringUtils.isNotBlank(scimUser.getName().getFormatted())) {
            return scimUser.getName().getFormatted();
        }
        if (StringUtils.isNotBlank(scimUser.getDisplayName())) {
            return scimUser.getDisplayName();
        }
        if (scimUser.getName() != null) {
            String combined = StringUtils.defaultString(scimUser.getName().getGivenName()) + " "
                    + StringUtils.defaultString(scimUser.getName().getFamilyName());
            if (StringUtils.isNotBlank(combined.trim())) {
                return combined.trim();
            }
        }
        return scimUser.getUserName();
    }

    // Resolve the nick name of a SCIM user
    private static String resolveNickName(ScimUser scimUser) {
        if (StringUtils.isNotBlank(scimUser.getNickName())) {
            return scimUser.getNickName();
        }
        if (StringUtils.isNotBlank(scimUser.getDisplayName())) {
            return scimUser.getDisplayName();
        }
        if (scimUser.getName() != null && StringUtils.isNotBlank(scimUser.getName().getGivenName())) {
            return scimUser.getName().getGivenName();
        }
        return resolveName(scimUser);
    }

    // Collect the group identifiers a SCIM user belongs to
    private static List<String> groupIds(List<ScimGroupRef> groups) {
        List<String> groupIds = new ArrayList<>();
        if (groups == null || groups.isEmpty()) {
            return groupIds;
        }
        groups.forEach(group -> {
            if (StringUtils.isNotBlank(group.getValue())) {
                groupIds.add(group.getValue());
            }
        });
        return groupIds;
    }

    // Convert blank values to null so database unique indexes behave consistently
    private String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }
}