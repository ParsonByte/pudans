package cn.tolim.pudans.iam.authentication.webauthn.service;

import cn.tolim.pudans.iam.authentication.webauthn.challenge.WebAuthnChallenge;
import cn.tolim.pudans.iam.authentication.webauthn.challenge.WebAuthnChallengeService;
import cn.tolim.pudans.iam.authentication.webauthn.config.WebAuthnProperties;
import cn.tolim.pudans.iam.authentication.webauthn.constant.WebAuthnConstants;
import cn.tolim.pudans.iam.authentication.webauthn.crypto.WebAuthnEncoding;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnAuthenticationOptions;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnCredentialDescriptor;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnCredentialParameter;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnCredentialResponse;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnRegistrationOptions;
import cn.tolim.pudans.iam.authentication.webauthn.entity.WebAuthnCredentialEntity;
import cn.tolim.pudans.iam.authentication.webauthn.enums.WebAuthnCredentialStatus;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnCredentialLimitException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnCredentialNotFoundException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnDuplicateCredentialException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnNotConfiguredException;
import cn.tolim.pudans.iam.authentication.webauthn.repository.WebAuthnCredentialRepository;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnAssertionRequest;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnAssertionResult;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnAssertionVerifier;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnRegistrationRequest;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnRegistrationResult;
import cn.tolim.pudans.iam.authentication.webauthn.verification.WebAuthnRegistrationVerifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// WebAuthn registration and authentication ceremonies
@Service
public class WebAuthnService {

    private static final List<WebAuthnCredentialParameter> SUPPORTED_PARAMETERS = List.of(
            new WebAuthnCredentialParameter(WebAuthnConstants.CREDENTIAL_TYPE_PUBLIC_KEY, -7),
            new WebAuthnCredentialParameter(WebAuthnConstants.CREDENTIAL_TYPE_PUBLIC_KEY, -257),
            new WebAuthnCredentialParameter(WebAuthnConstants.CREDENTIAL_TYPE_PUBLIC_KEY, -35),
            new WebAuthnCredentialParameter(WebAuthnConstants.CREDENTIAL_TYPE_PUBLIC_KEY, -258),
            new WebAuthnCredentialParameter(WebAuthnConstants.CREDENTIAL_TYPE_PUBLIC_KEY, -36),
            new WebAuthnCredentialParameter(WebAuthnConstants.CREDENTIAL_TYPE_PUBLIC_KEY, -259));

    private final WebAuthnCredentialRepository repository;
    private final WebAuthnChallengeService challengeService;
    private final WebAuthnRegistrationVerifier registrationVerifier;
    private final WebAuthnAssertionVerifier assertionVerifier;
    private final WebAuthnProperties properties;
    private final Clock clock;

    @Autowired
    public WebAuthnService(WebAuthnCredentialRepository repository,
                           WebAuthnChallengeService challengeService,
                           WebAuthnRegistrationVerifier registrationVerifier,
                           WebAuthnAssertionVerifier assertionVerifier,
                           WebAuthnProperties properties,
                           ObjectProvider<Clock> clockProvider) {
        this(repository, challengeService, registrationVerifier, assertionVerifier, properties,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    WebAuthnService(WebAuthnCredentialRepository repository,
                    WebAuthnChallengeService challengeService,
                    WebAuthnRegistrationVerifier registrationVerifier,
                    WebAuthnAssertionVerifier assertionVerifier,
                    WebAuthnProperties properties, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.challengeService = Objects.requireNonNull(challengeService,
                "challengeService must not be null");
        this.registrationVerifier = Objects.requireNonNull(registrationVerifier,
                "registrationVerifier must not be null");
        this.assertionVerifier = Objects.requireNonNull(assertionVerifier,
                "assertionVerifier must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    // Creates browser options for a registration ceremony
    @Transactional(readOnly = true)
    public WebAuthnRegistrationOptions registrationOptions(String userId, String username,
                                                           String displayName) {
        requireEnabled();
        requireUser(userId);
        WebAuthnChallenge challenge = challengeService.createRegistration(userId, username);
        List<WebAuthnCredentialDescriptor> excluded = new ArrayList<>();
        for (WebAuthnCredentialEntity credential : repository
                .findAllByUserIdAndStatusOrderByCreateTimeDesc(userId,
                        WebAuthnCredentialStatus.ACTIVE)) {
            excluded.add(descriptor(credential));
        }
        String safeUsername = username == null || username.isBlank() ? userId : username;
        String safeDisplayName = displayName == null || displayName.isBlank()
                ? safeUsername : displayName;
        return new WebAuthnRegistrationOptions(challenge.challengeId(), challenge.challenge(),
                properties.getRelyingPartyId(), properties.getRelyingPartyName(),
                WebAuthnEncoding.base64Url(userId), safeUsername, safeDisplayName,
                SUPPORTED_PARAMETERS, properties.getTimeout().toMillis(),
                properties.getAttestation(), userVerification(), excluded);
    }

    // Verifies a registration response and persists the credential
    @Transactional
    public WebAuthnCredentialResponse completeRegistration(String userId,
                                                           WebAuthnRegistrationRequest request) {
        requireEnabled();
        requireUser(userId);
        if (repository.countByUserId(userId) >= properties.getMaxCredentialsPerUser()) {
            throw new WebAuthnCredentialLimitException(
                    properties.getMaxCredentialsPerUser());
        }
        WebAuthnRegistrationResult result = registrationVerifier.verify(request, userId);
        if (repository.existsByCredentialId(result.credentialId())) {
            throw new WebAuthnDuplicateCredentialException();
        }
        LocalDateTime now = now();
        WebAuthnCredentialEntity credential = new WebAuthnCredentialEntity()
                .setUserId(userId)
                .setCredentialId(result.credentialId())
                .setPublicKeyCose(result.publicKeyCose())
                .setCoseAlgorithm(result.algorithm())
                .setSignCount(result.signCount())
                .setTransports(String.join(",", result.transports()))
                .setAaguid(result.aaguid())
                .setAttestationFormat(result.attestationFormat())
                .setBackupEligible(result.backupEligible())
                .setBackupState(result.backupState())
                .setStatus(WebAuthnCredentialStatus.ACTIVE)
                .setRegisteredAt(now);
        repository.save(credential);
        return toResponse(credential);
    }

    // Creates browser options for an authentication ceremony
    @Transactional(readOnly = true)
    public WebAuthnAuthenticationOptions authenticationOptions(String userId, String username) {
        requireEnabled();
        List<WebAuthnCredentialDescriptor> allowed = new ArrayList<>();
        if (userId != null && !userId.isBlank()) {
            for (WebAuthnCredentialEntity credential : repository
                    .findAllByUserIdAndStatusOrderByCreateTimeDesc(userId,
                            WebAuthnCredentialStatus.ACTIVE)) {
                allowed.add(descriptor(credential));
            }
        }
        WebAuthnChallenge challenge = challengeService.createAuthentication(userId, username);
        return new WebAuthnAuthenticationOptions(challenge.challengeId(),
                challenge.challenge(), properties.getRelyingPartyId(),
                properties.getTimeout().toMillis(), userVerification(), allowed);
    }

    // Verifies an assertion and resolves the credential owner for passwordless login
    @Transactional
    public PasskeyAuthentication authenticate(WebAuthnAssertionRequest request) {
        return authenticate(null, request);
    }

    // Verifies an assertion and updates the credential signature counter
    @Transactional
    public WebAuthnCredentialResponse completeAuthentication(String userId,
                                                             WebAuthnAssertionRequest request) {
        return authenticate(userId, request).credential();
    }

    private PasskeyAuthentication authenticate(String expectedUserId,
                                               WebAuthnAssertionRequest request) {
        requireEnabled();
        if (request == null || request.credentialId() == null
                || request.credentialId().isBlank()) {
            throw new WebAuthnCredentialNotFoundException();
        }
        WebAuthnCredentialEntity credential = repository
                .findByCredentialId(request.credentialId())
                .orElseThrow(WebAuthnCredentialNotFoundException::new);
        if (expectedUserId != null && !expectedUserId.equals(credential.getUserId())) {
            throw new WebAuthnCredentialNotFoundException();
        }
        if (credential.getStatus() != WebAuthnCredentialStatus.ACTIVE) {
            throw new WebAuthnCredentialNotFoundException();
        }
        WebAuthnAssertionResult result = assertionVerifier.verify(request, credential);
        credential.setSignCount(result.signCount()).setLastUsedAt(now());
        repository.save(credential);
        return new PasskeyAuthentication(credential.getUserId(), toResponse(credential));
    }

    // Lists safe metadata of all credentials of a user
    @Transactional(readOnly = true)
    public List<WebAuthnCredentialResponse> list(String userId) {
        requireUser(userId);
        return repository.findAllByUserIdOrderByCreateTimeDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    // Disables one credential of a user
    @Transactional
    public WebAuthnCredentialResponse disable(String userId, String credentialId) {
        requireUser(userId);
        WebAuthnCredentialEntity credential = repository
                .findByCredentialIdAndUserId(credentialId, userId)
                .orElseThrow(WebAuthnCredentialNotFoundException::new);
        credential.setStatus(WebAuthnCredentialStatus.DISABLED);
        repository.save(credential);
        return toResponse(credential);
    }

    // Disables all credentials of a user
    @Transactional
    public int disableAll(String userId) {
        requireUser(userId);
        int disabled = 0;
        for (WebAuthnCredentialEntity credential : repository
                .findAllByUserIdOrderByCreateTimeDesc(userId)) {
            if (credential.getStatus() != WebAuthnCredentialStatus.DISABLED) {
                credential.setStatus(WebAuthnCredentialStatus.DISABLED);
                repository.save(credential);
                disabled++;
            }
        }
        return disabled;
    }

    // Result of a passwordless passkey assertion
    public record PasskeyAuthentication(String userId, WebAuthnCredentialResponse credential) {
    }

    private WebAuthnCredentialDescriptor descriptor(WebAuthnCredentialEntity credential) {
        List<String> transports = credential.getTransports() == null
                || credential.getTransports().isBlank()
                ? List.of()
                : List.of(credential.getTransports().split(","));
        return new WebAuthnCredentialDescriptor(credential.getCredentialId(),
                WebAuthnConstants.CREDENTIAL_TYPE_PUBLIC_KEY, transports);
    }

    private WebAuthnCredentialResponse toResponse(WebAuthnCredentialEntity credential) {
        return new WebAuthnCredentialResponse(credential.getCredentialId(),
                credential.getTransports(), credential.getAaguid(),
                credential.getAttestationFormat(),
                credential.getCoseAlgorithm() == null ? 0 : credential.getCoseAlgorithm(),
                credential.getSignCount() == null ? 0 : credential.getSignCount(),
                Boolean.TRUE.equals(credential.getBackupEligible()),
                Boolean.TRUE.equals(credential.getBackupState()),
                credential.getStatus() == null ? null : credential.getStatus().name(),
                credential.getRegisteredAt(), credential.getLastUsedAt());
    }

    private String userVerification() {
        return properties.isRequireUserVerification() ? "required" : "preferred";
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new WebAuthnNotConfiguredException("WebAuthn authentication is disabled");
        }
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("WebAuthn user ID must not be blank");
        }
    }
}