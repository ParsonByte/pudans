package cn.tolim.pudans.apidocs;

import cn.tolim.pudans.iam.identitysource.core.event.IdentitySourceEventListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

// Replaces the IAM IdentitySourceBeanRegistry configuration, whose constructor reads identity
// sources from the database. The inert listener keeps the event runner's dependency satisfied
// while the doc profile runs without PostgreSQL.
@AutoConfiguration
@Profile("doc")
@ConditionalOnClass(IdentitySourceEventListener.class)
@ConditionalOnProperty(name = "pudans.api-docs.override-identity-source-registry", havingValue = "true")
public class IdentitySourceDocAutoConfiguration {

    @Bean("identitySourceBeanRegistry")
    public IdentitySourceEventListener pudansOpenApiDocIdentitySourceEventListener() {
        return InertProxy.create(IdentitySourceEventListener.class);
    }

}