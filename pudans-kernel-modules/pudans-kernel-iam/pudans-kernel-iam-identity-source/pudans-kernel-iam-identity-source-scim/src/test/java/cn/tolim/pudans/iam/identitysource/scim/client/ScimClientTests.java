package cn.tolim.pudans.iam.identitysource.scim.client;

import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import cn.tolim.pudans.iam.identitysource.scim.ScimConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ScimClientTests {

    private TestScimServer server;
    private ScimConfig config;
    private ScimClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = TestScimServer.start();
        config = new ScimConfig();
        config.setBaseUrl(server.baseUrl());
        config.setBearerToken("test-token");
        config.setPageSize(2);
        config.setDetailOnList(Boolean.FALSE);
        client = new ScimClient(config);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void shouldListRootAndGroups() {
        server.on("/scim/v2/Groups", """
                {"totalResults":2,"startIndex":1,"itemsPerPage":2,
                 "Resources":[{"id":"g1","displayName":"admins"},
                              {"id":"g2","displayName":"staff"}]}
                """);

        List<Dept> deptList = client.getDeptList();

        assertEquals(3, deptList.size());
        assertEquals("scim-root", deptList.getFirst().getDeptId());
        assertEquals("g1", deptList.get(1).getDeptId());
        assertEquals("staff", deptList.get(2).getName());
    }

    @Test
    void shouldSkipGroupsWhenGroupSyncDisabled() {
        config.setSyncGroupsAsDepts(Boolean.FALSE);

        List<Dept> deptList = client.getDeptList();

        assertEquals(1, deptList.size());
        assertEquals("scim-root", deptList.getFirst().getDeptId());
    }

    @Test
    void shouldReadAllUsersAcrossPages() {
        server.on("/scim/v2/Users", (path, query) -> {
            if (query.contains("startIndex=1")) {
                return TestScimServer.StubResponse.ok("""
                        {"totalResults":3,"startIndex":1,"itemsPerPage":2,
                         "Resources":[{"id":"u1","userName":"alice"},{"id":"u2","userName":"bob"}]}
                        """);
            }
            return TestScimServer.StubResponse.ok("""
                    {"totalResults":3,"startIndex":3,"itemsPerPage":1,
                     "Resources":[{"id":"u3","userName":"carol"}]}
                    """);
        });

        List<User> users = client.getUserList();

        assertEquals(3, users.size());
        assertEquals("u3", users.get(2).getUserId());
    }

    @Test
    void shouldReadFullUserRepresentationWhenDetailEnabled() {
        config.setDetailOnList(Boolean.TRUE);
        server.on("/scim/v2/Users", """
                {"totalResults":1,"startIndex":1,"itemsPerPage":1,
                 "Resources":[{"id":"u1","userName":"alice","displayName":"List Name"}]}
                """);
        server.on("/scim/v2/Users/u1", """
                {"id":"u1","userName":"alice","displayName":"Detail Name",
                 "name":{"formatted":"Alice Detail"}}
                """);

        List<User> users = client.getUserList();

        assertEquals("Alice Detail", users.getFirst().getUserDetail().getName());
    }

    @Test
    void shouldResolveUsersFromGroupMembers() {
        server.on("/scim/v2/Groups/g1", """
                {"id":"g1","displayName":"admins",
                 "members":[{"value":"u1","type":"User"},{"value":"g2","type":"Group"}]}
                """);
        server.on("/scim/v2/Users/u1", """
                {"id":"u1","userName":"alice","name":{"formatted":"Alice"},
                 "groups":[{"value":"g1","display":"admins"}]}
                """);

        List<User> users = client.getUserList("g1");

        assertEquals(1, users.size());
        assertEquals("u1", users.getFirst().getUserId());
        assertEquals(List.of("g1"), users.getFirst().getDeptIdList());
    }

    @Test
    void shouldGetRootDepartmentWithoutHttpCall() {
        Dept dept = client.getDept("scim-root");

        assertEquals("scim-root", dept.getDeptId());
        assertEquals("SCIM", dept.getName());
    }

    @Test
    void shouldGetGroupById() {
        server.on("/scim/v2/Groups/g1", """
                {"id":"g1","displayName":"admins"}
                """);

        Dept dept = client.getDept("g1");

        assertEquals("g1", dept.getDeptId());
        assertEquals("admins", dept.getName());
    }

    @Test
    void shouldGetUserById() {
        server.on("/scim/v2/Users/u1", """
                {"id":"u1","userName":"alice","emails":[{"value":"alice@example.com","primary":true}]}
                """);

        User user = client.getUser("u1");

        assertEquals("u1", user.getUserId());
        assertEquals("alice@example.com", user.getEmail());
        assertTrue(user.getActive());
    }
}