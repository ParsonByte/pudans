package cn.tolim.pudans.iam.authentication.totp.service;

import cn.tolim.pudans.iam.authentication.totp.algorithm.Base32Codec;
import cn.tolim.pudans.iam.authentication.totp.algorithm.TotpCodeVerifier;
import cn.tolim.pudans.iam.authentication.totp.algorithm.TotpUriFactory;
import cn.tolim.pudans.iam.authentication.totp.config.TotpAuthenticationProperties;
import cn.tolim.pudans.iam.authentication.totp.crypto.TotpSecretEnvelope;
import cn.tolim.pudans.iam.authentication.totp.crypto.TotpSecretProtector;
import cn.tolim.pudans.iam.authentication.totp.dto.TotpCredentialResponse;
import cn.tolim.pudans.iam.authentication.totp.dto.TotpEnrollResponse;
import cn.tolim.pudans.iam.authentication.totp.entity.TotpCredentialEntity;
import cn.tolim.pudans.iam.authentication.totp.enums.TotpAlgorithm;
import cn.tolim.pudans.iam.authentication.totp.enums.TotpStatus;
import cn.tolim.pudans.iam.authentication.totp.exception.TotpAlreadyEnrolledException;
import cn.tolim.pudans.iam.authentication.totp.exception.TotpCodeInvalidException;
import cn.tolim.pudans.iam.authentication.totp.exception.TotpCredentialNotFoundException;
import cn.tolim.pudans.iam.authentication.totp.exception.TotpDisabledException;
import cn.tolim.pudans.iam.authentication.totp.exception.TotpLockedException;
import cn.tolim.pudans.iam.authentication.totp.exception.TotpNotEnrolledException;
import cn.tolim.pudans.iam.authentication.totp.repository.TotpCredentialRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

// Enrollment and verification of TOTP credentials
@Service
public class TotpService {

    private final TotpCredentialRepository repository;
    private final TotpSecretProtector protector;
    private final TotpCodeVerifier verifier;
    private final TotpAuthenticationProperties properties;
    private final Clock clock;

    @Autowired
    public TotpService(TotpCredentialRepository repository, TotpSecretProtector protector,
                       TotpCodeVerifier verifier, TotpAuthenticationProperties properties,
                       ObjectProvider<Clock> clockProvider) {
        this(repository, protector, verifier, properties,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    TotpService(TotpCredentialRepository repository, TotpSecretProtector protector,
                TotpCodeVerifier verifier, TotpAuthenticationProperties properties, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.protector = Objects.requireNonNull(protector, "protector must not be null");
        this.verifier = Objects.requireNonNull(verifier, "verifier must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    // Generates a new pending credential and returns the secret once
    @Transactional
    public TotpEnrollResponse enroll(String userId, String name) {
        requireEnabled();
        requireUserId(userId);
        if (repository.countByUserIdAndStatus(userId, TotpStatus.ACTIVE) > 0) {
            throw new TotpAlreadyEnrolledException();
        }
        repository.deleteAllByUserIdAndStatus(userId, TotpStatus.PENDING);
        byte[] secret = protector.generateSecret(properties.getSecretBytes());
        try {
            String secretBase32 = Base32Codec.encode(secret);
            TotpSecretEnvelope envelope = protector.protect(userId, secret);
            String accountName = name == null || name.isBlank() ? userId : name.trim();
            TotpCredentialEntity credential = new TotpCredentialEntity()
                    .setUserId(userId)
                    .setName(accountName)
                    .setSecretAlgorithm(envelope.algorithm())
                    .setSecretKeyId(envelope.keyId())
                    .setSecretKeyVersion(envelope.keyVersion())
                    .setSecretNonce(envelope.nonce())
                    .setSecretCiphertext(envelope.ciphertext())
                    .setSecretTag(envelope.tag())
                    .setAlgorithm(properties.getAlgorithm())
                    .setDigits(properties.getDigits())
                    .setPeriodSeconds(properties.getPeriodSeconds())
                    .setStatus(TotpStatus.PENDING)
                    .setFailedAttempts(0);
            repository.save(credential);
            String uri = TotpUriFactory.build(properties.getIssuer(), accountName, secretBase32,
                    properties.getAlgorithm(), properties.getDigits(),
                    properties.getPeriodSeconds());
            return new TotpEnrollResponse(credential.getId(), secretBase32, uri,
                    properties.getAlgorithm().name(), properties.getDigits(),
                    properties.getPeriodSeconds());
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    // Confirms a pending enrollment with the first generated code
    @Transactional
    public TotpCredentialResponse confirmEnrollment(String userId, String credentialId,
                                                    String code) {
        requireEnabled();
        requireUserId(userId);
        TotpCredentialEntity credential = findPending(userId, credentialId);
        OptionalLong matchedStep = verifyCode(credential, userId, code);
        if (matchedStep.isEmpty()) {
            registerFailure(credential);
            throw new TotpCodeInvalidException();
        }
        LocalDateTime now = now();
        credential.setStatus(TotpStatus.ACTIVE)
                .setConfirmedAt(now)
                .setLastUsedStep(matchedStep.getAsLong())
                .setLastUsedAt(now)
                .setFailedAttempts(0)
                .setLockedUntil(null);
        repository.save(credential);
        return toResponse(credential);
    }

    // Verifies a code against the user's newest active credential
    @Transactional
    public boolean verify(String userId, String credentialId, String code) {
        requireEnabled();
        requireUserId(userId);
        TotpCredentialEntity credential = findActive(userId, credentialId);
        if (credential.isLocked(clock.instant())) {
            throw new TotpLockedException();
        }
        OptionalLong matchedStep = verifyCode(credential, userId, code);
        Long lastUsedStep = credential.getLastUsedStep();
        boolean replay = matchedStep.isPresent() && lastUsedStep != null
                && matchedStep.getAsLong() <= lastUsedStep;
        if (matchedStep.isEmpty() || replay) {
            registerFailure(credential);
            return false;
        }
        LocalDateTime now = now();
        credential.setLastUsedStep(matchedStep.getAsLong())
                .setLastUsedAt(now)
                .setFailedAttempts(0)
                .setLockedUntil(null);
        repository.save(credential);
        return true;
    }

    // Verifies a code against the newest active credential
    @Transactional
    public boolean verify(String userId, String code) {
        return verify(userId, null, code);
    }

    // Lists safe metadata of all credentials of one user
    @Transactional(readOnly = true)
    public List<TotpCredentialResponse> list(String userId) {
        requireUserId(userId);
        return repository.findAllByUserIdOrderByCreateTimeDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    // Disables one credential of a user
    @Transactional
    public TotpCredentialResponse disable(String userId, String credentialId) {
        requireUserId(userId);
        TotpCredentialEntity credential = repository.findByIdAndUserId(credentialId, userId)
                .orElseThrow(TotpCredentialNotFoundException::new);
        credential.setStatus(TotpStatus.DISABLED)
                .setLockedUntil(null)
                .setFailedAttempts(0);
        repository.save(credential);
        return toResponse(credential);
    }

    // Disables all credentials of a user
    @Transactional
    public int disableAll(String userId) {
        requireUserId(userId);
        int disabled = 0;
        for (TotpCredentialEntity credential : repository
                .findAllByUserIdOrderByCreateTimeDesc(userId)) {
            if (credential.getStatus() != TotpStatus.DISABLED) {
                credential.setStatus(TotpStatus.DISABLED)
                        .setLockedUntil(null)
                        .setFailedAttempts(0);
                repository.save(credential);
                disabled++;
            }
        }
        return disabled;
    }

    private TotpCredentialEntity findPending(String userId, String credentialId) {
        if (credentialId != null && !credentialId.isBlank()) {
            TotpCredentialEntity credential = repository.findByIdAndUserId(credentialId, userId)
                    .orElseThrow(TotpCredentialNotFoundException::new);
            if (credential.getStatus() != TotpStatus.PENDING) {
                throw new TotpCredentialNotFoundException();
            }
            return credential;
        }
        return repository.findFirstByUserIdAndStatusOrderByCreateTimeDesc(userId,
                        TotpStatus.PENDING)
                .orElseThrow(TotpNotEnrolledException::new);
    }

    private TotpCredentialEntity findActive(String userId, String credentialId) {
        if (credentialId != null && !credentialId.isBlank()) {
            TotpCredentialEntity credential = repository.findByIdAndUserId(credentialId, userId)
                    .orElseThrow(TotpCredentialNotFoundException::new);
            if (credential.getStatus() != TotpStatus.ACTIVE) {
                throw new TotpNotEnrolledException();
            }
            return credential;
        }
        return repository.findFirstByUserIdAndStatusOrderByCreateTimeDesc(userId,
                        TotpStatus.ACTIVE)
                .orElseThrow(TotpNotEnrolledException::new);
    }

    private OptionalLong verifyCode(TotpCredentialEntity credential, String userId, String code) {
        TotpSecretEnvelope envelope = new TotpSecretEnvelope(credential.getSecretAlgorithm(),
                credential.getSecretKeyId(), credential.getSecretKeyVersion(),
                credential.getSecretNonce(), credential.getSecretCiphertext(),
                credential.getSecretTag());
        byte[] secret = protector.unprotect(userId, envelope);
        try {
            return verifier.verify(secret, code, credential.getAlgorithm(),
                    credential.getDigits(), credential.getPeriodSeconds(),
                    properties.getWindow(), clock.instant());
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    private void registerFailure(TotpCredentialEntity credential) {
        int failures = credential.getFailedAttempts() == null
                ? 1 : credential.getFailedAttempts() + 1;
        credential.setFailedAttempts(failures);
        if (failures >= properties.getMaxFailedAttempts()) {
            credential.setLockedUntil(now().plus(properties.getLockDuration()));
        }
        repository.save(credential);
    }

    private TotpCredentialResponse toResponse(TotpCredentialEntity credential) {
        TotpAlgorithm algorithm = credential.getAlgorithm() == null
                ? properties.getAlgorithm() : credential.getAlgorithm();
        return new TotpCredentialResponse(credential.getId(), credential.getName(),
                credential.getStatus() == null ? null : credential.getStatus().name(),
                algorithm.name(),
                credential.getDigits() == null ? properties.getDigits() : credential.getDigits(),
                credential.getPeriodSeconds() == null ? properties.getPeriodSeconds()
                        : credential.getPeriodSeconds(),
                credential.getConfirmedAt(), credential.getLastUsedAt());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new TotpDisabledException();
        }
    }

    private static void requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("TOTP user ID must not be blank");
        }
    }
}