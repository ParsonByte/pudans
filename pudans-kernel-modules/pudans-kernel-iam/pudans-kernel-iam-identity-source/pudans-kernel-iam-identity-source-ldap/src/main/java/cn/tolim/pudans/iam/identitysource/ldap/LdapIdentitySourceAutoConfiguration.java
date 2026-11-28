package cn.tolim.pudans.iam.identitysource.ldap;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
public class LdapIdentitySourceAutoConfiguration {

    // Register the LDAP provider definition for the synchronizer provider registry
    @Bean
    @ConditionalOnMissingBean
    public LdapIdentitySourceProvider ldapIdentitySourceProvider() {
        return new LdapIdentitySourceProvider();
    }
}