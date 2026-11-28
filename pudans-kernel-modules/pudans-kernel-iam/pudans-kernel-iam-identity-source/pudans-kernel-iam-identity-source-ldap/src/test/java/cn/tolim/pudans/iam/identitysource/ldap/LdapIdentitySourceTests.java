package cn.tolim.pudans.iam.identitysource.ldap;

import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceEventPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncDeptPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncUserPostProcessor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;


class LdapIdentitySourceTests {

    @Test
    void shouldIgnoreEventCallbacks() {
        LdapIdentitySource identitySource = new LdapIdentitySource("id", "ldap",
                new LdapConfig(), mock(IdentitySourceClient.class),
                mock(IdentitySourceSyncUserPostProcessor.class),
                mock(IdentitySourceSyncDeptPostProcessor.class),
                mock(IdentitySourceEventPostProcessor.class));

        Object result = identitySource.event(null, "{}");

        assertEquals("ignored", ((Map<?, ?>) result).get("status"));
        assertEquals("id", identitySource.getId());
        assertEquals("ldap", identitySource.getName());
    }
}