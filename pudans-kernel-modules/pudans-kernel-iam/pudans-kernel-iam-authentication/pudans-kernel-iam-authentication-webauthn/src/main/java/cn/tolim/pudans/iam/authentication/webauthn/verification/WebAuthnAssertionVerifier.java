package cn.tolim.pudans.iam.authentication.webauthn.verification;

import cn.tolim.pudans.iam.authentication.webauthn.challenge.WebAuthnChallengeType;
import cn.tolim.pudans.iam.authentication.webauthn.constant.WebAuthnConstants;
import cn.tolim.pudans.iam.authentication.webauthn.cose.CoseKey;
import cn.tolim.pudans.iam.authentication.webauthn.crypto.WebAuthnEncoding;
import cn.tolim.pudans.iam.authentication.webauthn.entity.WebAuthnCredentialEntity;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnCounterException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnUnsupportedAlgorithmException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnVerificationException;
import cn.tolim.pudans.iam.authentication.webauthn.model.AuthenticatorData;

import java.io.ByteArrayOutputStream;
import java.security.PublicKey;
import java.util.Objects;

// Verifies assertion signatures with the stored credential public key
public class WebAuthnAssertionVerifier {

    private final WebAuthnCeremonyValidator validator;
    private final WebAuthnSignatureVerifier signatureVerifier;

    public WebAuthnAssertionVerifier(WebAuthnCeremonyValidator validator,
                                     WebAuthnSignatureVerifier signatureVerifier) {
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.signatureVerifier = Objects.requireNonNull(signatureVerifier,
                "signatureVerifier must not be null");
    }

    // Verifies an assertion for a stored credential
    public WebAuthnAssertionResult verify(WebAuthnAssertionRequest request,
                                          WebAuthnCredentialEntity credential) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(credential, "credential must not be null");
        WebAuthnCeremonyValidator.Validated validated = validator.validate(
                request.challengeId(), WebAuthnChallengeType.AUTHENTICATION,
                WebAuthnConstants.TYPE_GET, credential.getUserId(), request.clientDataJson());
        byte[] authenticatorDataBytes = decode(request.authenticatorData(),
                "authenticator data");
        AuthenticatorData authenticatorData = AuthenticatorData.parse(authenticatorDataBytes);
        validator.verifyUserPresence(authenticatorData);
        validator.verifyUserVerification(authenticatorData);
        validator.verifyRpIdHash(authenticatorData.rpIdHash());
        byte[] signature = decode(request.signature(), "signature");
        byte[] signedData = signedData(authenticatorDataBytes,
                validator.clientDataHash(validated.clientDataBytes()));
        PublicKey publicKey = CoseKey.parse(decode(credential.getPublicKeyCose(),
                "stored COSE public key")).toPublicKey();
        int algorithm = credential.getCoseAlgorithm();
        if (!signatureVerifier.supports(algorithm)) {
            throw new WebAuthnUnsupportedAlgorithmException(
                    "Stored credential algorithm is not supported: " + algorithm);
        }
        if (!signatureVerifier.verify(algorithm, publicKey, signedData, signature)) {
            throw new WebAuthnVerificationException("Assertion signature is invalid");
        }
        verifyCounter(credential.getSignCount(), authenticatorData.signCount());
        return new WebAuthnAssertionResult(credential.getCredentialId(), credential.getUserId(),
                authenticatorData.signCount(), authenticatorData.userVerified());
    }

    private static void verifyCounter(Long stored, long received) {
        if (stored != null && stored != 0 && received <= stored) {
            throw new WebAuthnCounterException();
        }
    }

    private static byte[] signedData(byte[] authenticatorData, byte[] clientDataHash) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                authenticatorData.length + clientDataHash.length);
        output.writeBytes(authenticatorData);
        output.writeBytes(clientDataHash);
        return output.toByteArray();
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