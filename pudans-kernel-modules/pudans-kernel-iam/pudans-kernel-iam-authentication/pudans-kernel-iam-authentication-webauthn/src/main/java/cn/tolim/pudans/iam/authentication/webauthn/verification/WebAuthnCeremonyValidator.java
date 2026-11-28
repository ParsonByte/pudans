package cn.tolim.pudans.iam.authentication.webauthn.verification;

import cn.tolim.pudans.iam.authentication.webauthn.challenge.WebAuthnChallenge;
import cn.tolim.pudans.iam.authentication.webauthn.challenge.WebAuthnChallengeService;
import cn.tolim.pudans.iam.authentication.webauthn.challenge.WebAuthnChallengeType;
import cn.tolim.pudans.iam.authentication.webauthn.config.WebAuthnProperties;
import cn.tolim.pudans.iam.authentication.webauthn.crypto.WebAuthnEncoding;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnChallengeException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnNotConfiguredException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnOriginException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnVerificationException;
import cn.tolim.pudans.iam.authentication.webauthn.model.AuthenticatorData;
import cn.tolim.pudans.iam.authentication.webauthn.model.CollectedClientData;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

// Shared client data, challenge and origin validation for WebAuthn ceremonies
public class WebAuthnCeremonyValidator {

    private final WebAuthnProperties properties;
    private final WebAuthnChallengeService challengeService;

    public WebAuthnCeremonyValidator(WebAuthnProperties properties,
                                     WebAuthnChallengeService challengeService) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.challengeService = Objects.requireNonNull(challengeService,
                "challengeService must not be null");
    }

    // Validates client data, consumes the challenge and checks the ceremony user
    public Validated validate(String challengeId, WebAuthnChallengeType type,
                              String expectedType, String expectedUserId,
                              String clientDataJsonBase64) {
        requireEnabled();
        byte[] clientDataBytes = decode(clientDataJsonBase64, "client data JSON");
        CollectedClientData clientData = CollectedClientData.parse(
                new String(clientDataBytes, StandardCharsets.UTF_8));
        if (!expectedType.equals(clientData.type())) {
            throw new WebAuthnVerificationException(
                    "Client data type does not match the ceremony");
        }
        WebAuthnChallenge challenge = challengeService.consume(challengeId, type);
        if (!challenge.challenge().equals(clientData.challenge())) {
            throw new WebAuthnChallengeException("WebAuthn challenge value does not match");
        }
        verifyOrigin(clientData.origin());
        if (expectedUserId != null && challenge.userId() != null
                && !challenge.userId().equals(expectedUserId)) {
            throw new WebAuthnChallengeException(
                    "WebAuthn challenge does not belong to this user");
        }
        return new Validated(challenge, clientData, clientDataBytes);
    }

    // Compares the authenticator RP ID hash with the configured relying party
    public void verifyRpIdHash(byte[] rpIdHash) {
        if (!MessageDigest.isEqual(rpIdHash, sha256(properties.getRelyingPartyId()))) {
            throw new WebAuthnVerificationException(
                    "Authenticator data relying party ID hash does not match");
        }
    }

    // Requires the user-present flag
    public void verifyUserPresence(AuthenticatorData authenticatorData) {
        if (!authenticatorData.userPresent()) {
            throw new WebAuthnVerificationException(
                    "Authenticator data does not assert user presence");
        }
    }

    // Enforces user verification when configured
    public void verifyUserVerification(AuthenticatorData authenticatorData) {
        if (properties.isRequireUserVerification() && !authenticatorData.userVerified()) {
            throw new WebAuthnVerificationException(
                    "Authenticator data does not assert user verification");
        }
    }

    // Calculates the SHA-256 hash of the client data JSON bytes
    public byte[] clientDataHash(byte[] clientDataBytes) {
        return sha256Bytes(clientDataBytes);
    }

    public boolean isUserVerificationRequired() {
        return properties.isRequireUserVerification();
    }

    public WebAuthnProperties properties() {
        return properties;
    }

    public void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new WebAuthnNotConfiguredException("WebAuthn authentication is disabled");
        }
        if (properties.getRelyingPartyId() == null || properties.getRelyingPartyId().isBlank()) {
            throw new WebAuthnNotConfiguredException(
                    "WebAuthn relying party ID is not configured");
        }
    }

    private void verifyOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            throw new WebAuthnOriginException(origin);
        }
        if (!properties.getOrigins().isEmpty()) {
            for (String allowed : properties.getOrigins()) {
                if (sameOrigin(allowed, origin)) {
                    return;
                }
            }
            throw new WebAuthnOriginException(origin);
        }
        if (!hostMatchesRelyingParty(origin)) {
            throw new WebAuthnOriginException(origin);
        }
    }

    private boolean hostMatchesRelyingParty(String origin) {
        try {
            URI uri = URI.create(origin);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return false;
            }
            String rpId = properties.getRelyingPartyId().toLowerCase(java.util.Locale.ROOT);
            String lowerHost = host.toLowerCase(java.util.Locale.ROOT);
            boolean hostMatches = lowerHost.equals(rpId) || lowerHost.endsWith("." + rpId);
            boolean secure = "https".equalsIgnoreCase(scheme)
                    || ("http".equalsIgnoreCase(scheme)
                    && ("localhost".equals(lowerHost) || "127.0.0.1".equals(lowerHost)
                    || "::1".equals(lowerHost)));
            return hostMatches && secure;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean sameOrigin(String expected, String actual) {
        try {
            URI left = URI.create(expected.trim());
            URI right = URI.create(actual.trim());
            return Objects.equals(lower(left.getScheme()), lower(right.getScheme()))
                    && Objects.equals(lower(left.getHost()), lower(right.getHost()))
                    && effectivePort(left) == effectivePort(right);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static byte[] decode(String value, String field) {
        try {
            return WebAuthnEncoding.decodeBase64Url(value);
        } catch (IllegalArgumentException exception) {
            throw new WebAuthnVerificationException(field + " is not valid Base64URL",
                    exception);
        }
    }

    private static byte[] sha256(String value) {
        return sha256Bytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256Bytes(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    // Result of a validated ceremony challenge
    public record Validated(WebAuthnChallenge challenge, CollectedClientData clientData,
                            byte[] clientDataBytes) {
    }
}