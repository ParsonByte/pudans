package cn.tolim.pudans.iam.identitysource.ldap;

import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.ldap.client.LdapConnectionFactory;
import cn.tolim.pudans.iam.identitysource.ldap.client.LdapEntry;
import cn.tolim.pudans.iam.identitysource.ldap.client.LdapOperations;
import cn.tolim.pudans.iam.identitysource.ldap.client.SearchScope;
import org.junit.jupiter.api.Test;

import javax.naming.directory.BasicAttributes;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class LdapConfigValidatorTests {

    @Test
    void shouldRejectMissingServerUrl() {
        LdapConfig config = new LdapConfig();
        config.setBaseDn("dc=example,dc=com");

        LdapConfigValidator validator = new LdapConfigValidator(ignored -> new SuccessfulOperations());

        assertThrows(ApiCallException.class, () -> validator.validate(config));
    }

    @Test
    void shouldRejectMissingBaseDn() {
        LdapConfig config = new LdapConfig();
        config.setServerUrl("ldap://127.0.0.1:389");

        LdapConfigValidator validator = new LdapConfigValidator(ignored -> new SuccessfulOperations());

        assertThrows(ApiCallException.class, () -> validator.validate(config));
    }

    @Test
    void shouldValidateReachableServer() {
        LdapConfig config = new LdapConfig();
        config.setServerUrl("ldap://127.0.0.1:389");
        config.setBaseDn("dc=example,dc=com");

        LdapConfigValidator validator = new LdapConfigValidator(ignored -> new SuccessfulOperations());

        assertTrue(validator.validate(config));
    }

    @Test
    void shouldReportConnectionFailure() {
        LdapConfig config = new LdapConfig();
        config.setServerUrl("ldap://127.0.0.1:389");
        config.setBaseDn("dc=example,dc=com");
        LdapConnectionFactory factory = ignored -> new FailingOperations();

        LdapConfigValidator validator = new LdapConfigValidator(factory);

        assertThrows(ApiCallException.class, () -> validator.validate(config));
    }

    private static final class SuccessfulOperations implements LdapOperations {

        @Override
        public List<LdapEntry> search(String baseDn, String filter, SearchScope scope,
                                      String... attributes) {
            return List.of(new LdapEntry(baseDn, new BasicAttributes(true)));
        }

        @Override
        public LdapEntry lookup(String dn, String... attributes) {
            return new LdapEntry(dn, new BasicAttributes(true));
        }

        @Override
        public void close() {
        }
    }

    private static final class FailingOperations implements LdapOperations {

        @Override
        public List<LdapEntry> search(String baseDn, String filter, SearchScope scope,
                                      String... attributes) {
            throw new ApiCallException("connection refused");
        }

        @Override
        public LdapEntry lookup(String dn, String... attributes) {
            throw new ApiCallException("connection refused");
        }

        @Override
        public void close() {
        }
    }
}