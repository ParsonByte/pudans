package cn.tolim.pudans.iam.authentication.webauthn.verification;

import cn.tolim.pudans.iam.authentication.webauthn.cose.CoseKey;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnUnsupportedAlgorithmException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnVerificationException;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;

// JCA backed verifier for the COSE algorithms supported without a WebAuthn library
public class JcaWebAuthnSignatureVerifier implements WebAuthnSignatureVerifier {

    @Override
    public boolean supports(int coseAlgorithm) {
        return jcaName(coseAlgorithm) != null;
    }

    @Override
    public boolean verify(int coseAlgorithm, PublicKey publicKey, byte[] data, byte[] signature) {
        String algorithm = jcaName(coseAlgorithm);
        if (algorithm == null) {
            throw new WebAuthnUnsupportedAlgorithmException(
                    "Unsupported COSE signature algorithm: " + coseAlgorithm);
        }
        try {
            Signature verifier = Signature.getInstance(algorithm);
            verifier.initVerify(publicKey);
            verifier.update(data);
            return verifier.verify(signature);
        } catch (GeneralSecurityException exception) {
            throw new WebAuthnVerificationException("Signature verification failed", exception);
        }
    }

    private static String jcaName(int coseAlgorithm) {
        return switch (coseAlgorithm) {
            case CoseKey.ALG_ES256 -> "SHA256withECDSA";
            case CoseKey.ALG_ES384 -> "SHA384withECDSA";
            case CoseKey.ALG_ES512 -> "SHA512withECDSA";
            case CoseKey.ALG_RS256 -> "SHA256withRSA";
            case CoseKey.ALG_RS384 -> "SHA384withRSA";
            case CoseKey.ALG_RS512 -> "SHA512withRSA";
            default -> null;
        };
    }
}