package cn.tolim.pudans.iam.identitysource.ldap;

import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import cn.tolim.pudans.iam.identitysource.ldap.client.LdapEntry;
import org.junit.jupiter.api.Test;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class LdapAttributeMapperTests {

    @Test
    void shouldMapUserAttributes() {
        LdapConfig config = new LdapConfig();
        config.setPhoneAreaCode("86");
        LdapAttributeMapper mapper = new LdapAttributeMapper(config);
        Attributes attributes = attributes(
                attribute("uid", "alice"),
                attribute("cn", "Alice Chen"),
                attribute("displayName", "Alice"),
                attribute("mail", "alice@example.com"),
                attribute("telephoneNumber", "13800000000"),
                attribute("memberOf", "cn=admins,dc=example,dc=com",
                        "cn=staff,dc=example,dc=com"));

        User user = mapper.toUser(new LdapEntry("uid=alice,dc=example,dc=com", attributes));

        assertEquals("alice", user.getUserId());
        assertEquals("alice@example.com", user.getEmail());
        assertEquals("alice@example.com", user.getOrgEmail());
        assertEquals("13800000000", user.getPhone());
        assertEquals("86", user.getPhoneAreaCode());
        assertTrue(user.getActive());
        assertEquals("Alice Chen", user.getUserDetail().getName());
        assertEquals("Alice", user.getUserDetail().getNickName());
        assertEquals(List.of("cn=admins,dc=example,dc=com", "cn=staff,dc=example,dc=com"),
                user.getDeptIdList());
    }

    @Test
    void shouldFallBackToUserNameWhenUserIdIsMissing() {
        LdapAttributeMapper mapper = new LdapAttributeMapper(new LdapConfig());
        Attributes attributes = attributes(attribute("cn", "Alice"));

        User user = mapper.toUser(new LdapEntry("uid=alice,dc=example,dc=com", attributes));

        assertEquals("Alice", user.getUserId());
        assertEquals("Alice", user.getUserDetail().getName());
    }

    @Test
    void shouldReadDisabledFlag() {
        LdapConfig config = new LdapConfig();
        config.setUserActiveAttribute("accountStatus");
        LdapAttributeMapper mapper = new LdapAttributeMapper(config);

        User user = mapper.toUser(new LdapEntry("uid=bob,dc=example,dc=com",
                attributes(attribute("uid", "bob"), attribute("cn", "Bob"),
                        attribute("accountStatus", "disabled"))));

        assertFalse(user.getActive());
    }

    @Test
    void shouldMapGroupAttributes() {
        LdapAttributeMapper mapper = new LdapAttributeMapper(new LdapConfig());
        LdapEntry entry = new LdapEntry("cn=admins,dc=example,dc=com", attributes(
                attribute("cn", "admins"),
                attribute("member", "uid=alice,dc=example,dc=com",
                        "uid=bob,dc=example,dc=com")));

        Dept dept = mapper.toDept(entry);

        assertEquals("cn=admins,dc=example,dc=com", dept.getDeptId());
        assertEquals("admins", dept.getName());
        assertEquals(0L, dept.getOrder());
        assertEquals(List.of("uid=alice,dc=example,dc=com", "uid=bob,dc=example,dc=com"),
                mapper.getMemberDns(entry));
    }

    @Test
    void shouldDetectGroupObjectClasses() {
        assertTrue(LdapAttributeMapper.isGroup(attributes(
                attribute("objectClass", "top", "groupOfNames"))));
        assertFalse(LdapAttributeMapper.isGroup(attributes(
                attribute("objectClass", "top", "person"))));
    }

    private static Attribute attribute(String name, String... values) {
        BasicAttribute attribute = new BasicAttribute(name);
        Arrays.stream(values).forEach(attribute::add);
        return attribute;
    }

    private static Attributes attributes(Attribute... attributes) {
        BasicAttributes result = new BasicAttributes(true);
        Arrays.stream(attributes).forEach(result::put);
        return result;
    }
}