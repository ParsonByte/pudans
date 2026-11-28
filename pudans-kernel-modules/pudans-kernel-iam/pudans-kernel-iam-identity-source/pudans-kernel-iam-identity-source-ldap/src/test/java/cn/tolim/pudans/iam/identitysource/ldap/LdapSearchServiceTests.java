package cn.tolim.pudans.iam.identitysource.ldap;

import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.ldap.client.LdapConnectionFactory;
import cn.tolim.pudans.iam.identitysource.ldap.client.LdapEntry;
import cn.tolim.pudans.iam.identitysource.ldap.client.LdapOperations;
import cn.tolim.pudans.iam.identitysource.ldap.client.SearchScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class LdapSearchServiceTests {

    private static final String ADMINS_DN = "cn=admins,dc=example,dc=com";
    private static final String STAFF_DN = "cn=staff,dc=example,dc=com";

    private LdapConfig config;
    private FakeLdapOperations operations;
    private LdapSearchService searchService;

    @BeforeEach
    void setUp() {
        config = new LdapConfig();
        config.setServerUrl("ldap://127.0.0.1:389");
        config.setBaseDn("dc=example,dc=com");
        operations = new FakeLdapOperations();
        LdapConnectionFactory factory = ignored -> operations;
        searchService = new LdapSearchService(config, factory);
    }

    @Test
    void shouldListRootAndGroups() {
        operations.searchResults = List.of(group(ADMINS_DN, "admins"),
                group(STAFF_DN, "staff"));

        List<Dept> deptList = searchService.getDeptList();

        assertEquals(3, deptList.size());
        assertEquals("ldap-root", deptList.getFirst().getDeptId());
        assertEquals(ADMINS_DN, deptList.get(1).getDeptId());
        assertEquals("staff", deptList.get(2).getName());
    }

    @Test
    void shouldSkipRootDepartmentWhenDisabled() {
        config.setIncludeRootDept(Boolean.FALSE);
        operations.searchResults = List.of(group(ADMINS_DN, "admins"));

        List<Dept> deptList = searchService.getDeptList();

        assertEquals(1, deptList.size());
        assertEquals(ADMINS_DN, deptList.getFirst().getDeptId());
    }

    @Test
    void shouldGetGroupByDn() {
        operations.lookupResults.put(ADMINS_DN, group(ADMINS_DN, "admins"));

        Dept dept = searchService.getDept(ADMINS_DN);

        assertEquals(ADMINS_DN, dept.getDeptId());
        assertEquals("admins", dept.getName());
    }

    @Test
    void shouldGetGroupByName() {
        operations.searchResults = List.of(group(ADMINS_DN, "admins"));

        Dept dept = searchService.getDept("admins");

        assertEquals(ADMINS_DN, dept.getDeptId());
        assertTrue(operations.lastFilter.contains("(cn=admins)"));
    }

    @Test
    void shouldFailWhenGroupDoesNotExist() {
        operations.searchResults = List.of();

        assertThrows(ApiCallException.class, () -> searchService.getDept("missing"));
    }

    @Test
    void shouldGetUserWithGroupMemberships() {
        operations.searchResults = List.of(user("alice", "Alice Chen", ADMINS_DN));

        User user = searchService.getUser("alice");

        assertEquals("alice", user.getUserId());
        assertEquals("alice@example.com", user.getEmail());
        assertEquals(List.of(ADMINS_DN), user.getDeptIdList());
        assertTrue(operations.lastFilter.contains("(uid=alice)"));
    }

    @Test
    void shouldFailWhenUserDoesNotExist() {
        operations.searchResults = List.of();

        assertThrows(ApiCallException.class, () -> searchService.getUser("missing"));
    }

    @Test
    void shouldListUsersUnderRootDepartment() {
        operations.searchResults = List.of(user("alice", "Alice Chen", ADMINS_DN),
                user("bob", "Bob Li", STAFF_DN));

        List<User> users = searchService.getUserList(config.getEffectiveRootDeptId());

        assertEquals(2, users.size());
        assertEquals("alice", users.getFirst().getUserId());
    }

    @Test
    void shouldResolveUsersFromGroupMembers() {
        operations.lookupResults.put(ADMINS_DN, group(ADMINS_DN, "admins",
                "uid=alice,dc=example,dc=com", "uid=bob,dc=example,dc=com"));
        operations.lookupResults.put("uid=alice,dc=example,dc=com",
                user("alice", "Alice Chen"));
        operations.lookupResults.put("uid=bob,dc=example,dc=com",
                user("bob", "Bob Li"));
        operations.membershipResults = List.of(group(ADMINS_DN, "admins"));

        List<User> users = searchService.getUserList(ADMINS_DN);

        assertEquals(2, users.size());
        assertEquals(List.of(ADMINS_DN), users.getFirst().getDeptIdList());
        assertEquals(List.of(ADMINS_DN), users.get(1).getDeptIdList());
    }

    @Test
    void shouldSkipNestedGroupMembers() {
        operations.lookupResults.put(ADMINS_DN, group(ADMINS_DN, "admins",
                "uid=alice,dc=example,dc=com", STAFF_DN));
        operations.lookupResults.put("uid=alice,dc=example,dc=com",
                user("alice", "Alice Chen"));
        operations.lookupResults.put(STAFF_DN, group(STAFF_DN, "staff"));
        operations.membershipResults = List.of(group(ADMINS_DN, "admins"));

        List<User> users = searchService.getUserList(ADMINS_DN);

        assertEquals(1, users.size());
        assertEquals("alice", users.getFirst().getUserId());
    }

    @Test
    void shouldDeriveMembershipsWhenMemberOfIsMissing() {
        operations.searchResults = List.of(user("alice", "Alice Chen"),
                user("bob", "Bob Li"));
        operations.membershipResults = List.of(group(ADMINS_DN, "admins"),
                group(STAFF_DN, "staff"));

        List<User> users = searchService.getUserList(config.getEffectiveRootDeptId());

        assertEquals(List.of(ADMINS_DN, STAFF_DN), users.getFirst().getDeptIdList());
        assertEquals(List.of(ADMINS_DN, STAFF_DN), users.get(1).getDeptIdList());
    }

    @Test
    void shouldTestConnection() {
        searchService.testConnection();

        assertEquals(SearchScope.OBJECT, operations.lastScope);
    }

    private static LdapEntry user(String id, String name, String... groups) {
        List<Attribute> attributes = new ArrayList<>();
        attributes.add(attribute("uid", id));
        attributes.add(attribute("cn", name));
        attributes.add(attribute("displayName", name));
        attributes.add(attribute("mail", id + "@example.com"));
        if (groups.length > 0) {
            attributes.add(attribute("memberOf", groups));
        }
        return new LdapEntry("uid=" + id + ",dc=example,dc=com",
                toAttributes(attributes));
    }

    private static LdapEntry group(String dn, String name, String... members) {
        List<Attribute> attributes = new ArrayList<>();
        attributes.add(attribute("cn", name));
        attributes.add(attribute("objectClass", "top", "groupOfNames"));
        if (members.length > 0) {
            attributes.add(attribute("member", members));
        }
        return new LdapEntry(dn, toAttributes(attributes));
    }

    private static Attribute attribute(String name, String... values) {
        BasicAttribute attribute = new BasicAttribute(name);
        Arrays.stream(values).forEach(attribute::add);
        return attribute;
    }

    private static Attributes toAttributes(List<Attribute> attributes) {
        BasicAttributes result = new BasicAttributes(true);
        attributes.forEach(result::put);
        return result;
    }

    private static final class FakeLdapOperations implements LdapOperations {

        private List<LdapEntry> searchResults = List.of();
        private List<LdapEntry> membershipResults = List.of();
        private final Map<String, LdapEntry> lookupResults = new HashMap<>();
        private String lastFilter;
        private SearchScope lastScope;

        @Override
        public List<LdapEntry> search(String baseDn, String filter, SearchScope scope,
                                      String... attributes) {
            this.lastFilter = filter;
            this.lastScope = scope;
            if (filter.contains("(member=")) {
                return membershipResults;
            }
            return searchResults;
        }

        @Override
        public LdapEntry lookup(String dn, String... attributes) {
            LdapEntry entry = lookupResults.get(dn);
            if (entry == null) {
                throw new ApiCallException("LDAP entry not found: " + dn);
            }
            return entry;
        }

        @Override
        public void close() {
        }
    }
}