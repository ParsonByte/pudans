package cn.tolim.pudans.iam.identitysource.scim;

import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceEventPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncDeptPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncUserPostProcessor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;


class ScimIdentitySourceTests {

    @Test
    void shouldAcceptUserEvent() {
        ScimIdentitySource identitySource = newIdentitySource();

        Object result = identitySource.event(null, """
                {"schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
                 "id":"u1","userName":"alice@example.com"}
                """);

        Map<?, ?> body = (Map<?, ?>) result;
        assertEquals("accepted", body.get("status"));
        assertEquals("User", body.get("resourceType"));
        assertEquals("u1", body.get("id"));
        assertEquals("alice@example.com", body.get("userName"));
    }

    @Test
    void shouldAcceptGroupEvent() {
        ScimIdentitySource identitySource = newIdentitySource();

        Object result = identitySource.event(null, """
                {"schemas":["urn:ietf:params:scim:schemas:core:2.0:Group"],
                 "id":"g1","displayName":"admins"}
                """);

        Map<?, ?> body = (Map<?, ?>) result;
        assertEquals("accepted", body.get("status"));
        assertEquals("Group", body.get("resourceType"));
        assertEquals("admins", body.get("displayName"));
    }

    @Test
    void shouldRejectUnsupportedPayload() {
        ScimIdentitySource identitySource = newIdentitySource();

        assertThrows(ApiCallException.class,
                () -> identitySource.event(null, "{\"schemas\":[\"unknown\"]}"));
    }

    @Test
    void shouldRejectInvalidJson() {
        ScimIdentitySource identitySource = newIdentitySource();

        assertThrows(ApiCallException.class, () -> identitySource.event(null, "not-json"));
    }

    @Test
    void shouldRejectUserWithoutUserName() {
        ScimIdentitySource identitySource = newIdentitySource();

        assertThrows(ApiCallException.class, () -> identitySource.event(null, """
                {"schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],"id":"u1"}
                """));
    }

    private static ScimIdentitySource newIdentitySource() {
        return new ScimIdentitySource("id", "scim", new ScimConfig(),
                mock(IdentitySourceClient.class),
                mock(IdentitySourceSyncUserPostProcessor.class),
                mock(IdentitySourceSyncDeptPostProcessor.class),
                mock(IdentitySourceEventPostProcessor.class));
    }
}