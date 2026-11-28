package cn.tolim.pudans.iam.authentication.webauthn.verification;

import cn.tolim.pudans.iam.authentication.webauthn.cose.CoseKey;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnUnsupportedAttestationException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnVerificationException;
import cn.tolim.pudans.iam.authentication.webauthn.model.AuthenticatorData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebAuthnAttestationVerificationTests {

    private static final String RP_ID = "example.com";
    private static final byte[] AAGUID =
            HexFormat.of().parseHex("00112233445566778899aabbccddeeff");
    private static final byte[] OTHER_AAGUID =
            HexFormat.of().parseHex("0102030405060708090a0b0c0d0e0f10");
    private static final byte[] ZERO_AAGUID = new byte[16];
    private static final byte[] CLIENT_DATA_HASH = sha256(
            "webauthn client data".getBytes(StandardCharsets.UTF_8));

    private WebAuthnAttestationVerifier verifier;
    private KeyPair credentialKeyPair;
    private KeyPair packedKeyPair;
    private KeyPair u2fKeyPair;
    private KeyPair caKeyPair;
    private X509Certificate packedCertificate;
    private X509Certificate u2fCertificate;
    private X509Certificate caCertificate;

    @BeforeEach
    void setUp() throws Exception {
        verifier = new WebAuthnAttestationVerifier(new JcaWebAuthnSignatureVerifier());
        credentialKeyPair = ecKeyPair();
        packedKeyPair = new KeyPair(loadCertificate("packed-cert.pem").getPublicKey(),
                loadPrivateKey("packed-key.pem"));
        u2fKeyPair = new KeyPair(loadCertificate("u2f-cert.pem").getPublicKey(),
                loadPrivateKey("u2f-key.pem"));
        caKeyPair = new KeyPair(loadCertificate("ca-cert.pem").getPublicKey(),
                loadPrivateKey("ca-key.pem"));
        packedCertificate = loadCertificate("packed-cert.pem");
        u2fCertificate = loadCertificate("u2f-cert.pem");
        caCertificate = loadCertificate("ca-cert.pem");
    }

    @Test
    void acceptsPackedSelfAttestation() {
        AuthenticatorData authData = authenticatedData(0x45, AAGUID);
        byte[] signature = sign(packedSignedData(authData), credentialKeyPair.getPrivate());
        Map<Object, Object> statement = packedStatement(-7, signature, null);
        assertDoesNotThrow(() -> verifier.verify("packed", statement,
                authData, CLIENT_DATA_HASH));
    }

    @Test
    void acceptsPackedCertificateAttestation() {
        AuthenticatorData authData = authenticatedData(0x45, AAGUID);
        byte[] signature = sign(packedSignedData(authData), packedKeyPair.getPrivate());
        Map<Object, Object> statement = packedStatement(-7, signature,
                List.of(der(packedCertificate)));
        assertDoesNotThrow(() -> verifier.verify("packed", statement,
                authData, CLIENT_DATA_HASH));
    }

    @Test
    void rejectsPackedTamperedSignature() {
        AuthenticatorData authData = authenticatedData(0x45, AAGUID);
        byte[] signature = sign(packedSignedData(authData), packedKeyPair.getPrivate());
        signature[signature.length - 1] ^= 0x01;
        Map<Object, Object> statement = packedStatement(-7, signature,
                List.of(der(packedCertificate)));
        assertThrows(WebAuthnVerificationException.class, () -> verifier.verify("packed",
                statement, authData, CLIENT_DATA_HASH));
    }

    @Test
    void rejectsPackedCertificateWithMismatchedAaguid() {
        AuthenticatorData authData = authenticatedData(0x45, OTHER_AAGUID);
        byte[] signature = sign(packedSignedData(authData), packedKeyPair.getPrivate());
        Map<Object, Object> statement = packedStatement(-7, signature,
                List.of(der(packedCertificate)));
        assertThrows(WebAuthnVerificationException.class, () -> verifier.verify("packed",
                statement, authData, CLIENT_DATA_HASH));
    }

    @Test
    void rejectsPackedSelfAttestationWithDifferentAlgorithm() {
        AuthenticatorData authData = authenticatedData(0x45, AAGUID);
        byte[] signature = sign(packedSignedData(authData), credentialKeyPair.getPrivate());
        Map<Object, Object> statement = packedStatement(-257, signature, null);
        assertThrows(WebAuthnVerificationException.class, () -> verifier.verify("packed",
                statement, authData, CLIENT_DATA_HASH));
    }

    @Test
    void rejectsPackedCertificateThatIsACertificateAuthority() {
        AuthenticatorData authData = authenticatedData(0x45, ZERO_AAGUID);
        byte[] signature = sign(packedSignedData(authData), caKeyPair.getPrivate());
        Map<Object, Object> statement = packedStatement(-7, signature,
                List.of(der(caCertificate)));
        assertThrows(WebAuthnVerificationException.class, () -> verifier.verify("packed",
                statement, authData, CLIENT_DATA_HASH));
    }

    @Test
    void rejectsPackedCertificateWithoutAaguidExtension() {
        AuthenticatorData authData = authenticatedData(0x45, AAGUID);
        byte[] signature = sign(packedSignedData(authData), u2fKeyPair.getPrivate());
        Map<Object, Object> statement = packedStatement(-7, signature,
                List.of(der(u2fCertificate)));
        assertThrows(WebAuthnVerificationException.class, () -> verifier.verify("packed",
                statement, authData, CLIENT_DATA_HASH));
    }

    @Test
    void rejectsPackedCertificateChainWithWrongIssuer() {
        AuthenticatorData authData = authenticatedData(0x45, AAGUID);
        byte[] signature = sign(packedSignedData(authData), packedKeyPair.getPrivate());
        Map<Object, Object> statement = packedStatement(-7, signature,
                List.of(der(packedCertificate), der(u2fCertificate)));
        assertThrows(WebAuthnVerificationException.class, () -> verifier.verify("packed",
                statement, authData, CLIENT_DATA_HASH));
    }

    @Test
    void acceptsFidoU2fAttestation() {
        AuthenticatorData authData = authenticatedData(0x45, ZERO_AAGUID);
        byte[] signature = sign(u2fSignedData(authData), u2fKeyPair.getPrivate());
        Map<Object, Object> statement = Map.of(
                "sig", signature,
                "x5c", List.of(der(u2fCertificate)));
        assertDoesNotThrow(() -> verifier.verify("fido-u2f", statement,
                authData, CLIENT_DATA_HASH));
    }

    @Test
    void rejectsFidoU2fTamperedSignature() {
        AuthenticatorData authData = authenticatedData(0x45, ZERO_AAGUID);
        byte[] signature = sign(u2fSignedData(authData), u2fKeyPair.getPrivate());
        signature[signature.length - 1] ^= 0x01;
        Map<Object, Object> statement = Map.of(
                "sig", signature,
                "x5c", List.of(der(u2fCertificate)));
        assertThrows(WebAuthnVerificationException.class, () -> verifier.verify("fido-u2f",
                statement, authData, CLIENT_DATA_HASH));
    }

    @Test
    void rejectsFidoU2fCertificateWithAaguidExtension() {
        AuthenticatorData authData = authenticatedData(0x45, ZERO_AAGUID);
        byte[] signature = sign(u2fSignedData(authData), packedKeyPair.getPrivate());
        Map<Object, Object> statement = Map.of(
                "sig", signature,
                "x5c", List.of(der(packedCertificate)));
        assertThrows(WebAuthnVerificationException.class, () -> verifier.verify("fido-u2f",
                statement, authData, CLIENT_DATA_HASH));
    }

    @Test
    void rejectsUnsupportedAttestationFormats() {
        AuthenticatorData authData = authenticatedData(0x45, AAGUID);
        for (String format : List.of("tpm", "android-key", "apple")) {
            assertThrows(WebAuthnUnsupportedAttestationException.class,
                    () -> verifier.verify(format, Map.of(), authData, CLIENT_DATA_HASH));
        }
    }

    @Test
    void rejectsNoneAttestationWithStatement() {
        AuthenticatorData authData = authenticatedData(0x45, AAGUID);
        assertThrows(WebAuthnVerificationException.class, () -> verifier.verify("none",
                Map.of("sig", new byte[]{1, 2, 3}), authData, CLIENT_DATA_HASH));
    }

    private AuthenticatorData authenticatedData(int flags, byte[] aaguid) {
        byte[] credentialId = "credential-id-1".getBytes(StandardCharsets.US_ASCII);
        byte[] authData = authenticatorData(flags, 0, credentialId,
                coseKey(credentialKeyPair), aaguid);
        return AuthenticatorData.parse(authData);
    }

    private static byte[] packedSignedData(AuthenticatorData authData) {
        byte[] raw = authData.raw();
        byte[] signed = new byte[raw.length + CLIENT_DATA_HASH.length];
        System.arraycopy(raw, 0, signed, 0, raw.length);
        System.arraycopy(CLIENT_DATA_HASH, 0, signed, raw.length, CLIENT_DATA_HASH.length);
        return signed;
    }

    private static byte[] u2fSignedData(AuthenticatorData authData) {
        CoseKey credentialKey = CoseKey.parse(authData.credentialPublicKey());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0x00);
        output.writeBytes(authData.rpIdHash());
        output.writeBytes(CLIENT_DATA_HASH);
        output.writeBytes(authData.credentialId());
        output.write(0x04);
        output.writeBytes(fixedLength(new BigInteger(1, credentialKey.x()), 32));
        output.writeBytes(fixedLength(new BigInteger(1, credentialKey.y()), 32));
        return output.toByteArray();
    }

    private static Map<Object, Object> packedStatement(int algorithm, byte[] signature,
                                                       List<byte[]> chain) {
        Map<Object, Object> statement = new LinkedHashMap<>();
        statement.put("alg", algorithm);
        statement.put("sig", signature);
        if (chain != null) {
            statement.put("x5c", chain);
        }
        return statement;
    }

    private static byte[] sign(byte[] data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (Exception exception) {
            throw new IllegalStateException("Test signature could not be created", exception);
        }
    }

    private static KeyPair ecKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static byte[] coseKey(KeyPair keyPair) {
        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put(1, 2);
        map.put(3, -7);
        map.put(-1, 1);
        map.put(-2, fixedLength(publicKey.getW().getAffineX(), 32));
        map.put(-3, fixedLength(publicKey.getW().getAffineY(), 32));
        return cbor(map);
    }

    private static byte[] authenticatorData(int flags, long signCount, byte[] credentialId,
                                            byte[] coseKey, byte[] aaguid) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(sha256(RP_ID.getBytes(StandardCharsets.UTF_8)));
        output.write(flags);
        output.write((int) (signCount >>> 24) & 0xFF);
        output.write((int) (signCount >>> 16) & 0xFF);
        output.write((int) (signCount >>> 8) & 0xFF);
        output.write((int) signCount & 0xFF);
        if (credentialId != null) {
            output.writeBytes(aaguid);
            output.write((credentialId.length >>> 8) & 0xFF);
            output.write(credentialId.length & 0xFF);
            output.writeBytes(credentialId);
            output.writeBytes(coseKey);
        }
        return output.toByteArray();
    }

    private static byte[] der(X509Certificate certificate) {
        try {
            return certificate.getEncoded();
        } catch (CertificateEncodingException exception) {
            throw new IllegalStateException("Test certificate could not be encoded", exception);
        }
    }

    private static X509Certificate loadCertificate(String name) throws Exception {
        try (InputStream input = resource(name)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(input);
        }
    }

    private static PrivateKey loadPrivateKey(String name) throws Exception {
        String pem = new String(resource(name).readAllBytes(), StandardCharsets.US_ASCII);
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static InputStream resource(String name) {
        InputStream input = WebAuthnAttestationVerificationTests.class
                .getResourceAsStream("/webauthn/" + name);
        if (input == null) {
            throw new IllegalStateException("Test resource is missing: " + name);
        }
        return input;
    }

    private static byte[] fixedLength(BigInteger value, int length) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == length) {
            return bytes;
        }
        if (bytes.length == length + 1 && bytes[0] == 0) {
            return Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        byte[] result = new byte[length];
        System.arraycopy(bytes, 0, result, length - bytes.length, bytes.length);
        return result;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] cbor(Object value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeCbor(output, value);
        return output.toByteArray();
    }

    private static void writeCbor(ByteArrayOutputStream output, Object value) {
        if (value instanceof Integer number) {
            writeCborInteger(output, number.longValue());
        } else if (value instanceof Long number) {
            writeCborInteger(output, number);
        } else if (value instanceof byte[] bytes) {
            writeHeader(output, 2, bytes.length);
            output.writeBytes(bytes);
        } else if (value instanceof String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            writeHeader(output, 3, bytes.length);
            output.writeBytes(bytes);
        } else if (value instanceof Map<?, ?> map) {
            writeHeader(output, 5, map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                writeCbor(output, entry.getKey());
                writeCbor(output, entry.getValue());
            }
        } else if (value instanceof List<?> list) {
            writeHeader(output, 4, list.size());
            for (Object element : list) {
                writeCbor(output, element);
            }
        } else {
            throw new IllegalArgumentException("Unsupported test CBOR value: " + value);
        }
    }

    private static void writeCborInteger(ByteArrayOutputStream output, long value) {
        if (value >= 0) {
            writeHeader(output, 0, value);
        } else {
            writeHeader(output, 1, -1 - value);
        }
    }

    private static void writeHeader(ByteArrayOutputStream output, int major, long value) {
        int base = major << 5;
        if (value < 24) {
            output.write(base | (int) value);
        } else if (value <= 0xFF) {
            output.write(base | 24);
            output.write((int) value);
        } else if (value <= 0xFFFF) {
            output.write(base | 25);
            output.write((int) (value >>> 8) & 0xFF);
            output.write((int) value & 0xFF);
        } else {
            output.write(base | 26);
            output.write((int) (value >>> 24) & 0xFF);
            output.write((int) (value >>> 16) & 0xFF);
            output.write((int) (value >>> 8) & 0xFF);
            output.write((int) value & 0xFF);
        }
    }
}