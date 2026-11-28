package cn.tolim.pudans.iam.authentication.webauthn.config;

import cn.tolim.pudans.iam.authentication.webauthn.challenge.InMemoryWebAuthnChallengeService;
import cn.tolim.pudans.iam.authentication.webauthn.challenge.WebAuthnChallengeService;
import cn.tolim.pudans.iam.authentication.webauthn.verification.JcaWebAuthnSignatureVerifier;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnAssertionVerifier;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnAttestationVerifier;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnCeremonyValidator;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnRegistrationVerifier;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnSignatureVerifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// Wires WebAuthn ceremony validation, challenge storage and signature checks
@Configuration
@EnableConfigurationProperties(WebAuthnProperties.class)
public class WebAuthnConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WebAuthnChallengeService webAuthnChallengeService(WebAuthnProperties properties,
                                                             ObjectProvider<Clock> clockProvider) {
        return new InMemoryWebAuthnChallengeService(properties, clockProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebAuthnSignatureVerifier webAuthnSignatureVerifier() {
        return new JcaWebAuthnSignatureVerifier();
    }

    @Bean
    @ConditionalOnMissingBean
    public WebAuthnCeremonyValidator webAuthnCeremonyValidator(
            WebAuthnProperties properties, WebAuthnChallengeService challengeService) {
        return new WebAuthnCeremonyValidator(properties, challengeService);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebAuthnAttestationVerifier webAuthnAttestationVerifier(
            WebAuthnSignatureVerifier signatureVerifier) {
        return new WebAuthnAttestationVerifier(signatureVerifier);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebAuthnRegistrationVerifier webAuthnRegistrationVerifier(
            WebAuthnCeremonyValidator validator, WebAuthnSignatureVerifier signatureVerifier,
            WebAuthnAttestationVerifier attestationVerifier) {
        return new WebAuthnRegistrationVerifier(validator, signatureVerifier,
                attestationVerifier);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebAuthnAssertionVerifier webAuthnAssertionVerifier(
            WebAuthnCeremonyValidator validator, WebAuthnSignatureVerifier signatureVerifier) {
        return new WebAuthnAssertionVerifier(validator, signatureVerifier);
    }
}