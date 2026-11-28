package cn.tolim.pudans.iam.authentication.webauthn.verification;

import cn.tolim.pudans.iam.authentication.webauthn.challenge.InMemoryWebAuthnChallengeService;
import cn.tolim.pudans.iam.authentication.webauthn.challenge.WebAuthnChallenge;
import cn.tolim.pudans.iam.authentication.webauthn.config.WebAuthnProperties;
import cn.tolim.pudans.iam.authentication.webauthn.crypto.WebAuthnEncoding;
import cn.tolim.pudans.iam.authentication.webauthn.entity.WebAuthnCredentialEntity;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnChallengeException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnCounterException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnOriginException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnUnsupportedAttestationException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebAuthnCeremonyVerificationTests {

    private static final String USER_ID = "user-1";
    private static final String USERNAME = "alice";
    private static final String RP_ID = "example.com";
    private static final String ORIGIN = "https://example.com";

    private WebAuthnProperties properties;
    private InMemoryWebAuthnChallengeService challengeService;
    private WebAuthnRegistrationVerifier registrationVerifier;
    private WebAuthnAssertionVerifier assertionVerifier;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        properties = new WebAuthnProperties();
        properties.setRelyingPartyId(RP_ID);
        properties.setOrigins(List.of(ORIGIN));
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        challengeService = new InMemoryWebAuthnChallengeService(properties, clock);
        WebAuthnCeremonyValidator validator = new WebAuthnCeremonyValidator(properties,
                challengeService);
        JcaWebAuthnSignatureVerifier signatureVerifier = new JcaWebAuthnSignatureVerifier();
        registrationVerifier = new WebAuthnRegistrationVerifier(validator, signatureVerifier);
        assertionVerifier = new WebAuthnAssertionVerifier(validator, signatureVerifier);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
    }

    @Test
    void verifiesRegistrationAndAssertionRoundTrip() throws Exception {
        WebAuthnRegistrationResult registration = register();
        assertEquals(USER_ID, registration.userId());
        assertEquals(-7, registration.algorithm());
        assertEquals(List.of("internal", "hybrid"), registration.transports());
        assertTrue(registration.publicKeyCose().length() > 0);

        WebAuthnCredentialEntity credential = credential(registration);
        WebAuthnAssertionResult assertion = assertValid(credential, 7);
        assertEquals(7, assertion.signCount());
        assertTrue(assertion.userVerified());
    }

    @Test
    void rejectsTamperedAssertionSignature() throws Exception {
        WebAuthnRegistrationResult registration = register();
        WebAuthnCredentialEntity credential = credential(registration);
        WebAuthnChallenge challenge = challengeService.createAuthentication(USER_ID, USERNAME);
        byte[] clientData = clientData("webauthn.get", challenge.challenge(), ORIGIN);
        byte[] authData = authenticatorData(0x05, 1, null, null);
        byte[] signature = sign(authData, clientData);
        signature[signature.length - 1] ^= 0x01;
        WebAuthnAssertionRequest request = new WebAuthnAssertionRequest(
                challenge.challengeId(), credential.getCredentialId(),
                WebAuthnEncoding.base64Url(clientData), WebAuthnEncoding.base64Url(authData),
                WebAuthnEncoding.base64Url(signature));
        assertThrows(WebAuthnVerificationException.class,
                () -> assertionVerifier.verify(request, credential));
    }

    @Test
    void rejectsCounterRegression() throws Exception {
        WebAuthnRegistrationResult registration = register();
        WebAuthnCredentialEntity credential = credential(registration);
        credential.setSignCount(10L);
        assertThrows(WebAuthnCounterException.class, () -> assertValid(credential, 5));
    }

    @Test
    void rejectsForeignUserForRegistration() {
        WebAuthnChallenge challenge = challengeService.createRegistration(USER_ID, USERNAME);
        byte[] clientData = clientData("webauthn.create", challenge.challenge(), ORIGIN);
        WebAuthnRegistrationRequest request = new WebAuthnRegistrationRequest(
                challenge.challengeId(), WebAuthnEncoding.base64Url(clientData),
                WebAuthnEncoding.base64Url(attestationObject("none", Map.of(),
                        authenticatorData(0x45, 0, credentialId(), coseKey()))), "internal");
        assertThrows(WebAuthnChallengeException.class,
                () -> registrationVerifier.verify(request, "user-2"));
    }

    @Test
    void rejectsDisallowedOrigin() {
        WebAuthnChallenge challenge = challengeService.createRegistration(USER_ID, USERNAME);
        byte[] clientData = clientData("webauthn.create", challenge.challenge(),
                "https://evil.example.net");
        WebAuthnRegistrationRequest request = new WebAuthnRegistrationRequest(
                challenge.challengeId(), WebAuthnEncoding.base64Url(clientData),
                WebAuthnEncoding.base64Url(attestationObject("none", Map.of(),
                        authenticatorData(0x45, 0, credentialId(), coseKey()))), "internal");
        assertThrows(WebAuthnOriginException.class,
                () -> registrationVerifier.verify(request, USER_ID));
    }

    @Test
    void acceptsPackedSelfAttestationRegistration() throws Exception {
        WebAuthnChallenge challenge = challengeService.createRegistration(USER_ID, USERNAME);
        byte[] clientData = clientData("webauthn.create", challenge.challenge(), ORIGIN);
        byte[] authData = authenticatorData(0x45, 0, credentialId(), coseKey());
        Map<Object, Object> statement = new LinkedHashMap<>();
        statement.put("alg", -7);
        statement.put("sig", sign(authData, clientData));
        WebAuthnRegistrationRequest request = new WebAuthnRegistrationRequest(
                challenge.challengeId(), WebAuthnEncoding.base64Url(clientData),
                WebAuthnEncoding.base64Url(attestationObject("packed", statement, authData)),
                "internal");
        WebAuthnRegistrationResult result = registrationVerifier.verify(request, USER_ID);
        assertEquals("packed", result.attestationFormat());
        assertEquals(USER_ID, result.userId());
    }

    @Test
    void rejectsPackedAttestationWithInvalidSignature() throws Exception {
        WebAuthnChallenge challenge = challengeService.createRegistration(USER_ID, USERNAME);
        byte[] clientData = clientData("webauthn.create", challenge.challenge(), ORIGIN);
        byte[] authData = authenticatorData(0x45, 0, credentialId(), coseKey());
        byte[] signature = sign(authData, clientData);
        signature[signature.length - 1] ^= 0x01;
        Map<Object, Object> statement = new LinkedHashMap<>();
        statement.put("alg", -7);
        statement.put("sig", signature);
        WebAuthnRegistrationRequest request = new WebAuthnRegistrationRequest(
                challenge.challengeId(), WebAuthnEncoding.base64Url(clientData),
                WebAuthnEncoding.base64Url(attestationObject("packed", statement, authData)),
                "internal");
        assertThrows(WebAuthnVerificationException.class,
                () -> registrationVerifier.verify(request, USER_ID));
    }

    @Test
    void rejectsUnsupportedAttestationFormat() {
        WebAuthnChallenge challenge = challengeService.createRegistration(USER_ID, USERNAME);
        byte[] clientData = clientData("webauthn.create", challenge.challenge(), ORIGIN);
        WebAuthnRegistrationRequest request = new WebAuthnRegistrationRequest(
                challenge.challengeId(), WebAuthnEncoding.base64Url(clientData),
                WebAuthnEncoding.base64Url(attestationObject("tpm", Map.of(),
                        authenticatorData(0x45, 0, credentialId(), coseKey()))), "internal");
        assertThrows(WebAuthnUnsupportedAttestationException.class,
                () -> registrationVerifier.verify(request, USER_ID));
    }

    private WebAuthnRegistrationResult register() {
        WebAuthnChallenge challenge = challengeService.createRegistration(USER_ID, USERNAME);
        byte[] clientData = clientData("webauthn.create", challenge.challenge(), ORIGIN);
        byte[] authData = authenticatorData(0x45, 0, credentialId(), coseKey());
        WebAuthnRegistrationRequest request = new WebAuthnRegistrationRequest(
                challenge.challengeId(), WebAuthnEncoding.base64Url(clientData),
                WebAuthnEncoding.base64Url(attestationObject("none", Map.of(), authData)),
                "internal,hybrid");
        return registrationVerifier.verify(request, USER_ID);
    }

    private WebAuthnAssertionResult assertValid(WebAuthnCredentialEntity credential,
                                                long signCount) throws Exception {
        WebAuthnChallenge challenge = challengeService.createAuthentication(USER_ID, USERNAME);
        byte[] clientData = clientData("webauthn.get", challenge.challenge(), ORIGIN);
        byte[] authData = authenticatorData(0x05, signCount, null, null);
        byte[] signature = sign(authData, clientData);
        WebAuthnAssertionRequest request = new WebAuthnAssertionRequest(
                challenge.challengeId(), credential.getCredentialId(),
                WebAuthnEncoding.base64Url(clientData), WebAuthnEncoding.base64Url(authData),
                WebAuthnEncoding.base64Url(signature));
        return assertionVerifier.verify(request, credential);
    }

    private WebAuthnCredentialEntity credential(WebAuthnRegistrationResult registration) {
        return new WebAuthnCredentialEntity()
                .setUserId(registration.userId())
                .setCredentialId(registration.credentialId())
                .setPublicKeyCose(registration.publicKeyCose())
                .setCoseAlgorithm(registration.algorithm())
                .setSignCount(registration.signCount());
    }

    private byte[] sign(byte[] authenticatorData, byte[] clientData) throws Exception {
        ByteArrayOutputStream signed = new ByteArrayOutputStream();
        signed.writeBytes(authenticatorData);
        signed.writeBytes(sha256(clientData));
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(signed.toByteArray());
        return signature.sign();
    }

    private static byte[] clientData(String type, String challenge, String origin) {
        return ("{\"type\":\"" + type + "\",\"challenge\":\"" + challenge
                + "\",\"origin\":\"" + origin + "\",\"crossOrigin\":false}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] attestationObject(String format, Map<?, ?> statement,
                                            byte[] authenticatorData) {
        Map<Object, Object> object = new LinkedHashMap<>();
        object.put("fmt", format);
        object.put("attStmt", statement);
        object.put("authData", authenticatorData);
        return cbor(object);
    }

    private static byte[] authenticatorData(int flags, long signCount, byte[] credentialId,
                                            byte[] coseKey) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(sha256(RP_ID.getBytes(StandardCharsets.UTF_8)));
        output.write(flags);
        output.write((int) (signCount >>> 24) & 0xFF);
        output.write((int) (signCount >>> 16) & 0xFF);
        output.write((int) (signCount >>> 8) & 0xFF);
        output.write((int) signCount & 0xFF);
        if (credentialId != null) {
            output.writeBytes(new byte[16]);
            output.write((credentialId.length >>> 8) & 0xFF);
            output.write(credentialId.length & 0xFF);
            output.writeBytes(credentialId);
            output.writeBytes(coseKey);
        }
        return output.toByteArray();
    }

    private static byte[] credentialId() {
        return "credential-id-1".getBytes(StandardCharsets.US_ASCII);
    }

    private byte[] coseKey() {
        return cbor(coseKeyMap());
    }

    private Map<Object, Object> coseKeyMap() {
        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put(1, 2);
        map.put(3, -7);
        map.put(-1, 1);
        map.put(-2, fixedLength(publicKey.getW().getAffineX(), 32));
        map.put(-3, fixedLength(publicKey.getW().getAffineY(), 32));
        return map;
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
        } else if (Boolean.TRUE.equals(value)) {
            output.write(0xF5);
        } else if (Boolean.FALSE.equals(value)) {
            output.write(0xF4);
        } else if (value == null) {
            output.write(0xF6);
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