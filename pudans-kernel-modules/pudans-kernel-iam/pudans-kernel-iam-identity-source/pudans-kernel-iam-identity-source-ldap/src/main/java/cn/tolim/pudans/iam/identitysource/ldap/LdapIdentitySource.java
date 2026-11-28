package cn.tolim.pudans.iam.identitysource.ldap;

import cn.tolim.pudans.iam.identitysource.core.AbstractDefaultIdentitySource;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceEventPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncDeptPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncUserPostProcessor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.EVENT_MESSAGE;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.EVENT_STATUS;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.EVENT_STATUS_IGNORED;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.LOGGER_NAME;


@Slf4j(topic = LOGGER_NAME)
public class LdapIdentitySource extends AbstractDefaultIdentitySource<LdapConfig> {

    public LdapIdentitySource(String id, String name, LdapConfig config,
                              IdentitySourceClient identitySourceClient,
                              IdentitySourceSyncUserPostProcessor identitySourceSyncUserPostProcessor,
                              IdentitySourceSyncDeptPostProcessor identitySourceSyncDeptPostProcessor,
                              IdentitySourceEventPostProcessor identitySourceEventPostProcessor) {
        super(id, name, config, identitySourceClient, identitySourceSyncUserPostProcessor,
                identitySourceSyncDeptPostProcessor, identitySourceEventPostProcessor);
    }

    // LDAP directories are pulled on a schedule and do not push event callbacks
    @Override
    public Object event(HttpServletRequest request, String body) {
        log.debug("LDAP identity source [{}] does not support event callbacks, ignoring request",
                getId());
        return Map.of(EVENT_STATUS, EVENT_STATUS_IGNORED,
                EVENT_MESSAGE, "LDAP does not support event callbacks");
    }
}