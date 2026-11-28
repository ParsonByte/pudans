package cn.tolim.pudans.iam.authentication.webauthn.verification;

import java.security.PublicKey;

// Verifies authenticator signatures with a COSE algorithm and public key
public interface WebAuthnSignatureVerifier {

    // Returns true when the COSE algorithm is supported
    boolean supports(int coseAlgorithm);

    // Verifies a signature over the supplied data
    boolean verify(int coseAlgorithm, PublicKey publicKey, byte[] data, byte[] signature);
}