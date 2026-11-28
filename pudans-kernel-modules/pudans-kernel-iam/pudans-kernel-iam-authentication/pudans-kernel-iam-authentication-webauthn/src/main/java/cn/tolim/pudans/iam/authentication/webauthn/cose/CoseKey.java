package cn.tolim.pudans.iam.authentication.webauthn.cose;

import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnUnsupportedAlgorithmException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnVerificationException;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Map;

// COSE public key used by WebAuthn authenticators
public record CoseKey(int kty, int alg, Integer curve, byte[] x, byte[] y, byte[] modulus,
                      byte[] exponent) {

    // COSE key type: OKP
    public static final int KTY_OKP = 1;

    // COSE key type: EC2
    public static final int KTY_EC2 = 2;

    // COSE key type: RSA
    public static final int KTY_RSA = 3;

    // COSE algorithm: ES256
    public static final int ALG_ES256 = -7;

    // COSE algorithm: EdDSA
    public static final int ALG_EDDSA = -8;

    // COSE algorithm: ES384
    public static final int ALG_ES384 = -35;

    // COSE algorithm: ES512
    public static final int ALG_ES512 = -36;

    // COSE algorithm: RS256
    public static final int ALG_RS256 = -257;

    // COSE algorithm: RS384
    public static final int ALG_RS384 = -258;

    // COSE algorithm: RS512
    public static final int ALG_RS512 = -259;

    // COSE curve: P-256
    public static final int CRV_P256 = 1;

    // COSE curve: P-384
    public static final int CRV_P384 = 2;

    // COSE curve: P-521
    public static final int CRV_P521 = 3;

    // COSE curve: Ed25519
    public static final int CRV_ED25519 = 6;

    // Parses a CBOR encoded COSE key
    public static CoseKey parse(byte[] coseEncoded) {
        if (coseEncoded == null || coseEncoded.length == 0) {
            throw new WebAuthnVerificationException("COSE public key is missing");
        }
        Object decoded = CborDecoder.decode(coseEncoded);
        if (!(decoded instanceof Map<?, ?> map)) {
            throw new WebAuthnVerificationException("COSE public key is not a map");
        }
        int kty = intValue(map, 1, "kty");
        int alg = intValue(map, 3, "alg");
        return switch (kty) {
            case KTY_EC2 -> new CoseKey(kty, alg, intValue(map, -1, "crv"),
                    bytesValue(map, -2, "x"), bytesValue(map, -3, "y"), null, null);
            case KTY_RSA -> new CoseKey(kty, alg, null, null, null,
                    bytesValue(map, -1, "n"), bytesValue(map, -2, "e"));
            case KTY_OKP -> new CoseKey(kty, alg, intValue(map, -1, "crv"),
                    bytesValue(map, -2, "x"), null, null, null);
            default -> throw new WebAuthnUnsupportedAlgorithmException(
                    "Unsupported COSE key type: " + kty);
        };
    }

    // Converts the COSE key into a JCA public key
    public PublicKey toPublicKey() {
        try {
            return switch (kty) {
                case KTY_EC2 -> ecPublicKey();
                case KTY_RSA -> rsaPublicKey();
                case KTY_OKP -> throw new WebAuthnUnsupportedAlgorithmException(
                        "OKP keys require a WebAuthn library or a dedicated verifier");
                default -> throw new WebAuthnUnsupportedAlgorithmException(
                        "Unsupported COSE key type: " + kty);
            };
        } catch (GeneralSecurityException exception) {
            throw new WebAuthnUnsupportedAlgorithmException("Invalid COSE public key",
                    exception);
        }
    }

    private PublicKey ecPublicKey() throws GeneralSecurityException {
        if (curve == null || x == null || y == null) {
            throw new WebAuthnVerificationException("EC2 COSE key is incomplete");
        }
        String curveName = switch (curve) {
            case CRV_P256 -> "secp256r1";
            case CRV_P384 -> "secp384r1";
            case CRV_P521 -> "secp521r1";
            default -> throw new WebAuthnUnsupportedAlgorithmException(
                    "Unsupported COSE curve: " + curve);
        };
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(curveName));
        ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
        ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        return KeyFactory.getInstance("EC")
                .generatePublic(new ECPublicKeySpec(point, spec));
    }

    private PublicKey rsaPublicKey() throws GeneralSecurityException {
        if (modulus == null || exponent == null) {
            throw new WebAuthnVerificationException("RSA COSE key is incomplete");
        }
        return KeyFactory.getInstance("RSA").generatePublic(
                new RSAPublicKeySpec(new BigInteger(1, modulus), new BigInteger(1, exponent)));
    }

    private static int intValue(Map<?, ?> map, int key, String name) {
        Object value = lookup(map, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new WebAuthnVerificationException("COSE key field " + name + " is missing");
    }

    private static byte[] bytesValue(Map<?, ?> map, int key, String name) {
        Object value = lookup(map, key);
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        throw new WebAuthnVerificationException("COSE key field " + name + " is missing");
    }

    // CBOR integer keys decode as Long, callers address them as int
    private static Object lookup(Map<?, ?> map, int key) {
        Object value = map.get(key);
        return value != null ? value : map.get((long) key);
    }
}