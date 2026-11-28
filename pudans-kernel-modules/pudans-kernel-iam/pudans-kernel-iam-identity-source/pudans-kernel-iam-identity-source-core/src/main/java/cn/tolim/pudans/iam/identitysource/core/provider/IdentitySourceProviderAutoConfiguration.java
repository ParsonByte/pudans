package cn.tolim.pudans.iam.identitysource.core.provider;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;


@AutoConfiguration
public class IdentitySourceProviderAutoConfiguration {

    // Collect every provider definition contributed by the identity source modules
    @Bean
    @ConditionalOnMissingBean
    public IdentitySourceProviderRegistry identitySourceProviderRegistry(
            List<IdentitySourceProviderDefinition> definitions) {
        return new IdentitySourceProviderRegistry(definitions);
    }
}