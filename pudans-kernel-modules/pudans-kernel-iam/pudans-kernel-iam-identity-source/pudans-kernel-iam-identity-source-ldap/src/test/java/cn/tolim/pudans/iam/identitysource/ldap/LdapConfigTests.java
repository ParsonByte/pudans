package cn.tolim.pudans.iam.identitysource.ldap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class LdapConfigTests {

    @Test
    void shouldProvideDefaultValues() {
        LdapConfig config = new LdapConfig();
        config.setServerUrl("ldap://127.0.0.1:389");
        config.setBaseDn("dc=example,dc=com");

        assertEquals("dc=example,dc=com", config.getUserBaseDn());
        assertEquals("dc=example,dc=com", config.getGroupBaseDn());
        assertEquals("(objectClass=person)", config.getUserSearchFilter());
        assertEquals("(objectClass=groupOfNames)", config.getGroupSearchFilter());
        assertEquals("uid", config.getUserIdAttribute());
        assertEquals("member", config.getGroupMemberAttribute());
        assertEquals("ldap-root", config.getEffectiveRootDeptId());
        assertTrue(config.isRootDeptEnabled());
        assertFalse(config.isStartTlsEnabled());
        assertEquals(500, config.getPageSize());
    }

    @Test
    void shouldPreferExplicitSearchBasesAndRootValues() {
        LdapConfig config = new LdapConfig();
        config.setBaseDn("dc=example,dc=com");
        config.setUserSearchBase("ou=people");
        config.setGroupSearchBase("ou=groups");
        config.setRootDeptId("root-1");
        config.setRootDeptName("Directory");
        config.setIncludeRootDept(Boolean.FALSE);
        config.setStartTls(Boolean.TRUE);
        config.setUserFilter("(objectClass=inetOrgPerson)");

        assertEquals("ou=people", config.getUserBaseDn());
        assertEquals("ou=groups", config.getGroupBaseDn());
        assertEquals("root-1", config.getEffectiveRootDeptId());
        assertEquals("Directory", config.getEffectiveRootDeptName());
        assertFalse(config.isRootDeptEnabled());
        assertTrue(config.isStartTlsEnabled());
        assertEquals("(objectClass=inetOrgPerson)", config.getUserSearchFilter());
    }
}