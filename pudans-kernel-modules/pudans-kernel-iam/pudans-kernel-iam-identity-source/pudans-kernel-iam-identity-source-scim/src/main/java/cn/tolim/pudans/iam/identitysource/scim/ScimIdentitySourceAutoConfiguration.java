package cn.tolim.pudans.iam.identitysource.scim;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;


@AutoConfiguration
public class ScimIdentitySourceAutoConfiguration {

    // Register the SCIM provider definition for the synchronizer provider registry
    @Bean
    @ConditionalOnMissingBean
    public ScimIdentitySourceProvider scimIdentitySourceProvider() {
        return new ScimIdentitySourceProvider();
    }
}