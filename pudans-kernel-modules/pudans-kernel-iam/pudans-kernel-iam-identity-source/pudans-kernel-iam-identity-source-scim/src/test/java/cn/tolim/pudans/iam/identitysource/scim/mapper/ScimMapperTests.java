package cn.tolim.pudans.iam.identitysource.scim.mapper;

import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import cn.tolim.pudans.iam.identitysource.scim.ScimConfig;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimGroup;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimGroupRef;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimMultiValue;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimName;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ScimMapperTests {

    @Test
    void shouldMapUserFields() {
        ScimConfig config = new ScimConfig();
        config.setPhoneAreaCode("86");
        ScimMapper mapper = new ScimMapper(config);

        ScimUser scimUser = new ScimUser();
        scimUser.setId("u1");
        scimUser.setUserName("alice@example.com");
        scimUser.setActive(Boolean.FALSE);
        ScimName name = new ScimName();
        name.setFormatted("Alice Chen");
        name.setGivenName("Alice");
        name.setFamilyName("Chen");
        scimUser.setName(name);
        scimUser.setDisplayName("Alice");
        scimUser.setNickName("Ali");
        scimUser.setEmails(List.of(multiValue("alice@work.com", "work", false),
                multiValue("alice@home.com", "home", true)));
        scimUser.setPhoneNumbers(List.of(multiValue("13800000000", "mobile", null)));
        scimUser.setPhotos(List.of(multiValue("https://example.com/alice.png", "photo", null)));
        ScimGroupRef groupRef = new ScimGroupRef();
        groupRef.setValue("g1");
        scimUser.setGroups(List.of(groupRef));

        User user = mapper.toUser(scimUser);

        assertEquals("u1", user.getUserId());
        assertEquals("alice@home.com", user.getEmail());
        assertEquals("alice@home.com", user.getOrgEmail());
        assertEquals("13800000000", user.getPhone());
        assertEquals("86", user.getPhoneAreaCode());
        assertEquals("https://example.com/alice.png", user.getAvatar());
        assertFalse(user.getActive());
        assertEquals("Alice Chen", user.getUserDetail().getName());
        assertEquals("Ali", user.getUserDetail().getNickName());
        assertEquals(List.of("g1"), user.getDeptIdList());
    }

    @Test
    void shouldFallBackToUserNameWhenIdMissing() {
        ScimMapper mapper = new ScimMapper(new ScimConfig());
        ScimUser scimUser = new ScimUser();
        scimUser.setUserName("bob@example.com");

        User user = mapper.toUser(scimUser);

        assertEquals("bob@example.com", user.getUserId());
        assertEquals("bob@example.com", user.getUserDetail().getName());
        assertTrue(user.getActive());
        assertNull(user.getEmail());
        assertEquals(List.of(), user.getDeptIdList());
    }

    @Test
    void shouldMapGroupToDept() {
        ScimMapper mapper = new ScimMapper(new ScimConfig());
        ScimGroup group = new ScimGroup();
        group.setId("g1");
        group.setDisplayName("admins");

        Dept dept = mapper.toDept(group);

        assertEquals("g1", dept.getDeptId());
        assertEquals("admins", dept.getName());
        assertEquals(0L, dept.getOrder());
    }

    private static ScimMultiValue multiValue(String value, String type, Boolean primary) {
        ScimMultiValue multiValue = new ScimMultiValue();
        multiValue.setValue(value);
        multiValue.setType(type);
        multiValue.setPrimary(primary);
        return multiValue;
    }
}