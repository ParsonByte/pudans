package cn.tolim.pudans.iam.authentication.totp.config;

import cn.tolim.pudans.iam.authentication.totp.algorithm.TotpCodeVerifier;
import cn.tolim.pudans.iam.authentication.totp.crypto.SpissatusTotpSecretProtector;
import cn.tolim.pudans.iam.authentication.totp.crypto.TotpSecretProtector;
import cn.tolim.spissatus4j.contract.api.Spissatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Wires TOTP verification and spissatus backed secret protection
@Configuration
@EnableConfigurationProperties(TotpAuthenticationProperties.class)
public class TotpAuthenticationConfiguration {

    // Stateless TOTP code verifier
    @Bean
    @ConditionalOnMissingBean
    public TotpCodeVerifier totpCodeVerifier() {
        return new TotpCodeVerifier();
    }

    // Secret protector reusing an application Spissatus bean when available
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(TotpSecretProtector.class)
    public TotpSecretProtector totpSecretProtector(ObjectProvider<Spissatus> spissatusProvider,
                                                   TotpAuthenticationProperties properties) {
        return new SpissatusTotpSecretProtector(spissatusProvider, properties);
    }
}