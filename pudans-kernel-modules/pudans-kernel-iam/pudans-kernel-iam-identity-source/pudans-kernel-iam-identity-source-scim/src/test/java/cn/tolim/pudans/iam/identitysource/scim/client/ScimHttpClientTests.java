package cn.tolim.pudans.iam.identitysource.scim.client;

import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.scim.ScimAuthType;
import cn.tolim.pudans.iam.identitysource.scim.ScimConfig;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimServiceProviderConfig;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimUser;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimUserListResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ScimHttpClientTests {

    private TestScimServer server;
    private ScimConfig config;
    private ScimHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = TestScimServer.start();
        config = new ScimConfig();
        config.setBaseUrl(server.baseUrl());
        config.setBearerToken("test-token");
        config.setPageSize(2);
        client = new ScimHttpClient(config);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void shouldReadUserWithBearerToken() {
        server.on("/scim/v2/Users/u1", userJson("u1", "alice@example.com"));

        ScimUser user = client.getUser("u1");

        assertEquals("u1", user.getId());
        assertEquals("alice@example.com", user.getUserName());
        assertEquals("Bearer test-token", server.lastAuthorization());
    }

    @Test
    void shouldReadUserWithBasicAuthorization() {
        config.setAuthType(ScimAuthType.BASIC);
        config.setUsername("user");
        config.setPassword("secret");
        server.on("/scim/v2/Users/u1", userJson("u1", "alice@example.com"));

        client.getUser("u1");

        assertEquals("Basic dXNlcjpzZWNyZXQ=", server.lastAuthorization());
    }

    @Test
    void shouldListUsersWithPaginationParameters() {
        server.on("/scim/v2/Users", (path, query) -> {
            assertTrue(query.contains("startIndex=1"));
            assertTrue(query.contains("count=2"));
            return TestScimServer.StubResponse.ok("""
                    {"totalResults":2,"startIndex":1,"itemsPerPage":2,
                     "Resources":[{"id":"u1","userName":"alice"},{"id":"u2","userName":"bob"}]}
                    """);
        });

        ScimUserListResponse response = client.listUsers(1, 2);

        assertEquals(2, response.getTotalResults());
        assertEquals(2, response.getResources().size());
        assertEquals("bob", response.getResources().get(1).getUserName());
    }

    @Test
    void shouldSearchUsersWithEncodedFilter() {
        java.util.concurrent.atomic.AtomicReference<String> capturedQuery =
                new java.util.concurrent.atomic.AtomicReference<>();
        server.on("/scim/v2/Users", (path, query) -> {
            capturedQuery.set(query);
            return TestScimServer.StubResponse.ok("""
                    {"totalResults":1,"startIndex":1,"itemsPerPage":1,
                     "Resources":[{"id":"u1","userName":"alice"}]}
                    """);
        });

        ScimUserListResponse response = client.searchUsers("userName eq \"alice\"", 1, 1);

        assertEquals(1, response.getResources().size());
        assertTrue(capturedQuery.get().contains("startIndex=1"));
        assertTrue(capturedQuery.get().contains("count=1"));
        assertTrue(capturedQuery.get().contains("filter=userName eq \"alice\""));
    }

    @Test
    void shouldCreateUserWithScimMediaType() {
        server.on("/scim/v2/Users", userJson("u2", "bob@example.com"));

        ScimUser created = client.createUser(user("bob@example.com"));

        assertEquals("u2", created.getId());
        assertEquals("application/scim+json", server.lastContentType());
    }

    @Test
    void shouldReadServiceProviderConfig() {
        server.on("/scim/v2/ServiceProviderConfig", """
                {"schemas":["urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"],
                 "patch":{"supported":true},"filter":{"supported":true,"maxResults":200}}
                """);

        ScimServiceProviderConfig providerConfig = client.getServiceProviderConfig();

        assertNotNull(providerConfig);
        assertTrue(providerConfig.getPatch().getSupported());
        assertEquals(Boolean.TRUE, providerConfig.getFilter().getSupported());
    }

    @Test
    void shouldRaiseApiCallExceptionWithScimErrorDetail() {
        server.on("/scim/v2/Users/u404", new TestScimServer.StubResponse(404, """
                {"schemas":["urn:ietf:params:scim:api:messages:2.0:Error"],
                 "status":"404","scimType":"notFound","detail":"user not found"}
                """));

        ApiCallException exception = assertThrows(ApiCallException.class,
                () -> client.getUser("u404"));

        assertTrue(exception.getMessage().contains("404"));
        assertTrue(exception.getMessage().contains("user not found"));
        assertTrue(exception.getMessage().contains("notFound"));
    }

    @Test
    void shouldRequireBearerToken() {
        config.setBearerToken(null);

        assertThrows(ApiCallException.class, () -> client.getUser("u1"));
    }

    private static ScimUser user(String userName) {
        ScimUser user = new ScimUser();
        user.setUserName(userName);
        user.setActive(Boolean.TRUE);
        return user;
    }

    private static String userJson(String id, String userName) {
        return """
                {"schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
                 "id":"%s","userName":"%s","active":true}
                """.formatted(id, userName);
    }
}