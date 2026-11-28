package cn.tolim.pudans.iam.authentication.webauthn.service;

import cn.tolim.pudans.iam.authentication.webauthn.challenge.WebAuthnChallenge;
import cn.tolim.pudans.iam.authentication.webauthn.challenge.WebAuthnChallengeService;
import cn.tolim.pudans.iam.authentication.webauthn.challenge.WebAuthnChallengeType;
import cn.tolim.pudans.iam.authentication.webauthn.config.WebAuthnProperties;
import cn.tolim.pudans.iam.authentication.webauthn.crypto.WebAuthnEncoding;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnAuthenticationOptions;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnCredentialResponse;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnRegistrationOptions;
import cn.tolim.pudans.iam.authentication.webauthn.entity.WebAuthnCredentialEntity;
import cn.tolim.pudans.iam.authentication.webauthn.enums.WebAuthnCredentialStatus;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnCredentialNotFoundException;
import cn.tolim.pudans.iam.authentication.webauthn.repository.WebAuthnCredentialRepository;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnAssertionRequest;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnAssertionResult;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnAssertionVerifier;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnRegistrationRequest;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnRegistrationResult;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnRegistrationVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebAuthnServiceTests {

    private static final String USER_ID = "user-1";
    private static final String USERNAME = "alice";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private WebAuthnCredentialRepository repository;
    private WebAuthnChallengeService challengeService;
    private WebAuthnRegistrationVerifier registrationVerifier;
    private WebAuthnAssertionVerifier assertionVerifier;
    private WebAuthnProperties properties;
    private WebAuthnService service;

    @BeforeEach
    void setUp() {
        repository = mock(WebAuthnCredentialRepository.class);
        challengeService = mock(WebAuthnChallengeService.class);
        registrationVerifier = mock(WebAuthnRegistrationVerifier.class);
        assertionVerifier = mock(WebAuthnAssertionVerifier.class);
        properties = new WebAuthnProperties();
        properties.setRelyingPartyId("example.com");
        properties.setOrigins(List.of("https://example.com"));
        service = new WebAuthnService(repository, challengeService, registrationVerifier,
                assertionVerifier, properties, CLOCK);
        when(repository.save(any(WebAuthnCredentialEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsRegistrationOptions() {
        when(challengeService.createRegistration(USER_ID, USERNAME))
                .thenReturn(challenge("challenge-1", WebAuthnChallengeType.REGISTRATION));
        when(repository.findAllByUserIdAndStatusOrderByCreateTimeDesc(USER_ID,
                WebAuthnCredentialStatus.ACTIVE)).thenReturn(List.of(activeCredential()));

        WebAuthnRegistrationOptions options = service.registrationOptions(USER_ID, USERNAME,
                "Alice");

        assertEquals("challenge-1", options.challengeId());
        assertEquals("example.com", options.rpId());
        assertEquals(WebAuthnEncoding.base64Url(USER_ID), options.userId());
        assertEquals(6, options.pubKeyCredParams().size());
        assertEquals(1, options.excludeCredentials().size());
        assertEquals("internal", options.excludeCredentials().get(0).transports().get(0));
    }

    @Test
    void persistsVerifiedRegistration() {
        when(repository.countByUserId(USER_ID)).thenReturn(0L);
        when(repository.existsByCredentialId("credential-1")).thenReturn(false);
        when(registrationVerifier.verify(any(WebAuthnRegistrationRequest.class), eq(USER_ID)))
                .thenReturn(new WebAuthnRegistrationResult(USER_ID, USERNAME, "credential-1",
                        "cose-key", -7, 0, "aaguid", "none", true, false,
                        List.of("internal", "hybrid")));

        WebAuthnCredentialResponse response = service.completeRegistration(USER_ID,
                new WebAuthnRegistrationRequest("challenge-1", "client-data",
                        "attestation-object", "internal,hybrid"));

        assertEquals("credential-1", response.credentialId());
        assertEquals(WebAuthnCredentialStatus.ACTIVE.name(), response.status());
        assertTrue(response.backupEligible());
        ArgumentCaptor<WebAuthnCredentialEntity> captor =
                ArgumentCaptor.forClass(WebAuthnCredentialEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("internal,hybrid", captor.getValue().getTransports());
        assertEquals(USER_ID, captor.getValue().getUserId());
    }

    @Test
    void persistsVerifiedAuthenticationCounter() {
        WebAuthnCredentialEntity credential = activeCredential();
        credential.setSignCount(0L);
        when(repository.findByCredentialId("credential-1"))
                .thenReturn(Optional.of(credential));
        when(assertionVerifier.verify(any(WebAuthnAssertionRequest.class), eq(credential)))
                .thenReturn(new WebAuthnAssertionResult("credential-1", USER_ID, 4, true));

        WebAuthnCredentialResponse response = service.completeAuthentication(USER_ID,
                new WebAuthnAssertionRequest("challenge-2", "credential-1", "client-data",
                        "authenticator-data", "signature"));

        assertEquals(4, response.signCount());
        assertEquals(4, credential.getSignCount());
    }

    @Test
    void resolvesCredentialOwnerForPasswordlessAuthentication() {
        WebAuthnCredentialEntity credential = activeCredential();
        when(repository.findByCredentialId("credential-1"))
                .thenReturn(Optional.of(credential));
        when(assertionVerifier.verify(any(WebAuthnAssertionRequest.class), eq(credential)))
                .thenReturn(new WebAuthnAssertionResult("credential-1", USER_ID, 1, true));

        WebAuthnService.PasskeyAuthentication authentication = service.authenticate(
                new WebAuthnAssertionRequest("challenge-2", "credential-1", "client-data",
                        "authenticator-data", "signature"));

        assertEquals(USER_ID, authentication.userId());
        assertEquals("credential-1", authentication.credential().credentialId());
    }

    @Test
    void rejectsDisabledCredential() {
        WebAuthnCredentialEntity credential = activeCredential();
        credential.setStatus(WebAuthnCredentialStatus.DISABLED);
        when(repository.findByCredentialId("credential-1"))
                .thenReturn(Optional.of(credential));
        assertThrows(WebAuthnCredentialNotFoundException.class,
                () -> service.completeAuthentication(USER_ID,
                        new WebAuthnAssertionRequest("challenge-2", "credential-1", "client-data",
                                "authenticator-data", "signature")));
    }

    @Test
    void createsAuthenticationOptionsWithAllowList() {
        when(repository.findAllByUserIdAndStatusOrderByCreateTimeDesc(USER_ID,
                WebAuthnCredentialStatus.ACTIVE)).thenReturn(List.of(activeCredential()));
        when(challengeService.createAuthentication(USER_ID, USERNAME))
                .thenReturn(challenge("challenge-3", WebAuthnChallengeType.AUTHENTICATION));

        WebAuthnAuthenticationOptions options = service.authenticationOptions(USER_ID, USERNAME);

        assertEquals("challenge-3", options.challengeId());
        assertEquals(1, options.allowCredentials().size());
        assertEquals("credential-1", options.allowCredentials().get(0).id());
    }

    @Test
    void listsAndDisablesCredentials() {
        WebAuthnCredentialEntity credential = activeCredential();
        when(repository.findAllByUserIdOrderByCreateTimeDesc(USER_ID))
                .thenReturn(List.of(credential));
        when(repository.findByCredentialIdAndUserId("credential-1", USER_ID))
                .thenReturn(Optional.of(credential));

        assertEquals(1, service.list(USER_ID).size());
        service.disable(USER_ID, "credential-1");
        assertEquals(WebAuthnCredentialStatus.DISABLED, credential.getStatus());
        assertFalse(service.list(USER_ID).isEmpty());
    }

    private static WebAuthnCredentialEntity activeCredential() {
        return new WebAuthnCredentialEntity()
                .setUserId(USER_ID)
                .setCredentialId("credential-1")
                .setPublicKeyCose("cose-key")
                .setCoseAlgorithm(-7)
                .setSignCount(0L)
                .setTransports("internal")
                .setAaguid("aaguid")
                .setAttestationFormat("none")
                .setBackupEligible(false)
                .setBackupState(false)
                .setStatus(WebAuthnCredentialStatus.ACTIVE)
                .setRegisteredAt(java.time.LocalDateTime.now(ZoneOffset.UTC));
    }

    private static WebAuthnChallenge challenge(String id, WebAuthnChallengeType type) {
        return new WebAuthnChallenge(id, "challenge-value", USER_ID, USERNAME, type,
                Instant.parse("2026-01-01T00:05:00Z"));
    }
}