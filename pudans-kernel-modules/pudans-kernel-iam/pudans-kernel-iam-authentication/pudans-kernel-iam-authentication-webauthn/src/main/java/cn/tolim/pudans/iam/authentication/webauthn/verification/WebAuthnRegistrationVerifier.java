package cn.tolim.pudans.iam.authentication.webauthn.verification;

import cn.tolim.pudans.iam.authentication.webauthn.challenge.WebAuthnChallengeType;
import cn.tolim.pudans.iam.authentication.webauthn.constant.WebAuthnConstants;
import cn.tolim.pudans.iam.authentication.webauthn.cose.CborDecoder;
import cn.tolim.pudans.iam.authentication.webauthn.cose.CoseKey;
import cn.tolim.pudans.iam.authentication.webauthn.crypto.WebAuthnEncoding;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnUnsupportedAlgorithmException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnVerificationException;
import cn.tolim.pudans.iam.authentication.webauthn.model.AuthenticatorData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Verifies the registration ceremony and extracts credential data
public class WebAuthnRegistrationVerifier {

    private final WebAuthnCeremonyValidator validator;
    private final WebAuthnSignatureVerifier signatureVerifier;
    private final WebAuthnAttestationVerifier attestationVerifier;

    public WebAuthnRegistrationVerifier(WebAuthnCeremonyValidator validator,
                                        WebAuthnSignatureVerifier signatureVerifier) {
        this(validator, signatureVerifier, new WebAuthnAttestationVerifier(signatureVerifier));
    }

    public WebAuthnRegistrationVerifier(WebAuthnCeremonyValidator validator,
                                        WebAuthnSignatureVerifier signatureVerifier,
                                        WebAuthnAttestationVerifier attestationVerifier) {
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.signatureVerifier = Objects.requireNonNull(signatureVerifier,
                "signatureVerifier must not be null");
        this.attestationVerifier = Objects.requireNonNull(attestationVerifier,
                "attestationVerifier must not be null");
    }

    // Parses and verifies a registration response bound to the expected user
    public WebAuthnRegistrationResult verify(WebAuthnRegistrationRequest request,
                                             String expectedUserId) {
        Objects.requireNonNull(request, "request must not be null");
        WebAuthnCeremonyValidator.Validated validated = validator.validate(
                request.challengeId(), WebAuthnChallengeType.REGISTRATION,
                WebAuthnConstants.TYPE_CREATE, expectedUserId, request.clientDataJson());
        byte[] attestationObject = decode(request.attestationObject(), "attestation object");
        Map<?, ?> attestation = asMap(CborDecoder.decode(attestationObject),
                "attestation object");
        String format = asText(attestation.get("fmt"), "attestation format");
        if (!(attestation.get("attStmt") instanceof Map<?, ?> statement)) {
            throw new WebAuthnVerificationException("Attestation statement is missing");
        }
        byte[] authDataBytes = asBytes(attestation.get("authData"), "authenticator data");
        AuthenticatorData authData = AuthenticatorData.parse(authDataBytes);
        if (!authData.attestedCredentialDataIncluded()) {
            throw new WebAuthnVerificationException(
                    "Registration response has no attested credential data");
        }
        validator.verifyUserPresence(authData);
        validator.verifyUserVerification(authData);
        validator.verifyRpIdHash(authData.rpIdHash());
        attestationVerifier.verify(format, statement, authData,
                validator.clientDataHash(validated.clientDataBytes()));
        byte[] credentialId = authData.credentialId();
        byte[] credentialPublicKey = authData.credentialPublicKey();
        CoseKey coseKey = CoseKey.parse(credentialPublicKey);
        if (!signatureVerifier.supports(coseKey.alg())) {
            throw new WebAuthnUnsupportedAlgorithmException(
                    "Authenticator algorithm is not supported: " + coseKey.alg());
        }
        String aaguid = authData.aaguid() == null
                ? "" : WebAuthnEncoding.base64Url(authData.aaguid());
        return new WebAuthnRegistrationResult(validated.challenge().userId(),
                validated.challenge().username(),
                WebAuthnEncoding.base64Url(credentialId),
                WebAuthnEncoding.base64Url(credentialPublicKey),
                coseKey.alg(), authData.signCount(), aaguid, format,
                authData.backupEligible(), authData.backupState(),
                parseTransports(request.transports()));
    }

    private static List<String> parseTransports(String transports) {
        if (transports == null || transports.isBlank()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String transport : transports.split(",")) {
            String value = transport.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return new ArrayList<>(values);
    }

    private static Map<?, ?> asMap(Object value, String name) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new WebAuthnVerificationException(name + " is not a CBOR map");
    }

    private static String asText(Object value, String name) {
        if (value instanceof String text) {
            return text;
        }
        throw new WebAuthnVerificationException(name + " is not a CBOR text value");
    }

    private static byte[] asBytes(Object value, String name) {
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        throw new WebAuthnVerificationException(name + " is not a CBOR byte string");
    }

    private static byte[] decode(String value, String name) {
        try {
            return WebAuthnEncoding.decodeBase64Url(value);
        } catch (IllegalArgumentException exception) {
            throw new WebAuthnVerificationException(name + " is not valid Base64URL",
                    exception);
        }
    }
}