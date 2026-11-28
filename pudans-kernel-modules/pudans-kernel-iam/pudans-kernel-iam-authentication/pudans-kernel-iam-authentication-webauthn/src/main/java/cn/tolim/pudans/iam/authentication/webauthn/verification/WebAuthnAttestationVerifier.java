package cn.tolim.pudans.iam.authentication.webauthn.verification;

import cn.tolim.pudans.iam.authentication.webauthn.constant.WebAuthnConstants;
import cn.tolim.pudans.iam.authentication.webauthn.cose.CoseKey;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnUnsupportedAlgorithmException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnUnsupportedAttestationException;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnVerificationException;
import cn.tolim.pudans.iam.authentication.webauthn.model.AuthenticatorData;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Verifies packed and FIDO U2F attestation statements with JDK JCA primitives
public class WebAuthnAttestationVerifier {

    // FIDO AAGUID certificate extension
    private static final String OID_AAGUID = "1.3.6.1.4.1.45724.1.1.4";

    // X.509 basic constraints extension
    private static final String OID_BASIC_CONSTRAINTS = "2.5.29.19";

    // Subject organizational unit required of attestation certificates
    private static final String ATTESTATION_OU = "Authenticator Attestation";

    // FIDO U2F uncompressed EC point marker
    private static final byte U2F_UNCOMPRESSED_POINT = 0x04;

    // SHA-256 hash and P-256 coordinate length
    private static final int SHA256_LENGTH = 32;

    private final WebAuthnSignatureVerifier signatureVerifier;

    public WebAuthnAttestationVerifier(WebAuthnSignatureVerifier signatureVerifier) {
        this.signatureVerifier = Objects.requireNonNull(signatureVerifier,
                "signatureVerifier must not be null");
    }

    // Verifies the attestation statement of a registration response
    public void verify(String format, Map<?, ?> statement, AuthenticatorData authenticatorData,
                       byte[] clientDataHash) {
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(statement, "statement must not be null");
        Objects.requireNonNull(authenticatorData, "authenticatorData must not be null");
        Objects.requireNonNull(clientDataHash, "clientDataHash must not be null");
        switch (format) {
            case WebAuthnConstants.ATTESTATION_NONE -> verifyNone(statement);
            case WebAuthnConstants.ATTESTATION_PACKED ->
                    verifyPacked(statement, authenticatorData, clientDataHash);
            case WebAuthnConstants.ATTESTATION_FIDO_U2F ->
                    verifyFidoU2f(statement, authenticatorData, clientDataHash);
            default -> throw new WebAuthnUnsupportedAttestationException(format);
        }
    }

    private void verifyNone(Map<?, ?> statement) {
        if (!statement.isEmpty()) {
            throw new WebAuthnVerificationException(
                    "None attestation statement must be empty");
        }
    }

    private void verifyPacked(Map<?, ?> statement, AuthenticatorData authenticatorData,
                              byte[] clientDataHash) {
        if (statement.containsKey("ecdaaKeyId")) {
            throw new WebAuthnUnsupportedAttestationException(
                    WebAuthnConstants.ATTESTATION_PACKED + " (ecdaa)");
        }
        int algorithm = asInt(statement.get("alg"), "packed attestation algorithm");
        byte[] signature = asBytes(statement.get("sig"), "packed attestation signature");
        byte[] signedData = concat(authenticatorData.raw(), clientDataHash);
        Object x5c = statement.get("x5c");
        if (x5c == null) {
            verifyPackedSelfAttestation(algorithm, signature, signedData, authenticatorData);
            return;
        }
        List<X509Certificate> chain = parseCertificateChain(x5c, "packed attestation");
        X509Certificate leaf = chain.get(0);
        verifyAttestationSubject(leaf);
        verifyAttestationBasicConstraints(leaf);
        verifyPackedAaguid(leaf, authenticatorData.aaguid());
        verifyCertificateChain(chain);
        verifySignature(algorithm, leaf.getPublicKey(), signedData, signature,
                "packed attestation");
    }

    private void verifyPackedSelfAttestation(int algorithm, byte[] signature, byte[] signedData,
                                             AuthenticatorData authenticatorData) {
        CoseKey credentialKey = CoseKey.parse(authenticatorData.credentialPublicKey());
        if (credentialKey.alg() != algorithm) {
            throw new WebAuthnVerificationException(
                    "Packed self attestation algorithm does not match the credential key");
        }
        verifySignature(algorithm, credentialKey.toPublicKey(), signedData, signature,
                "packed self attestation");
    }

    private void verifyFidoU2f(Map<?, ?> statement, AuthenticatorData authenticatorData,
                               byte[] clientDataHash) {
        byte[] signature = asBytes(statement.get("sig"), "fido-u2f attestation signature");
        List<X509Certificate> chain = parseCertificateChain(statement.get("x5c"),
                "fido-u2f attestation");
        X509Certificate leaf = chain.get(0);
        verifyAttestationSubject(leaf);
        verifyAttestationBasicConstraints(leaf);
        if (leaf.getExtensionValue(OID_AAGUID) != null) {
            throw new WebAuthnVerificationException(
                    "FIDO U2F attestation certificate must not contain an AAGUID extension");
        }
        verifyCertificateChain(chain);
        CoseKey credentialKey = CoseKey.parse(authenticatorData.credentialPublicKey());
        if (credentialKey.kty() != CoseKey.KTY_EC2 || credentialKey.curve() == null
                || credentialKey.curve() != CoseKey.CRV_P256) {
            throw new WebAuthnVerificationException(
                    "FIDO U2F attestation requires an EC P-256 credential key");
        }
        byte[] credentialId = authenticatorData.credentialId();
        if (credentialId == null || credentialId.length == 0) {
            throw new WebAuthnVerificationException(
                    "FIDO U2F attestation requires a credential ID");
        }
        ByteArrayOutputStream verificationData = new ByteArrayOutputStream(
                1 + SHA256_LENGTH + SHA256_LENGTH + credentialId.length + 65);
        verificationData.write(0x00);
        verificationData.writeBytes(authenticatorData.rpIdHash());
        verificationData.writeBytes(clientDataHash);
        verificationData.writeBytes(credentialId);
        verificationData.writeBytes(u2fPublicKey(credentialKey));
        verifySignature(CoseKey.ALG_ES256, leaf.getPublicKey(), verificationData.toByteArray(),
                signature, "fido-u2f attestation");
    }

    private void verifySignature(int algorithm, PublicKey publicKey, byte[] data,
                                 byte[] signature, String name) {
        if (!signatureVerifier.supports(algorithm)) {
            throw new WebAuthnUnsupportedAlgorithmException(
                    "Attestation algorithm is not supported: " + algorithm);
        }
        if (!signatureVerifier.verify(algorithm, publicKey, data, signature)) {
            throw new WebAuthnVerificationException(name + " signature is invalid");
        }
    }

    // Verifies the packed attestation AAGUID extension requirements
    private static void verifyPackedAaguid(X509Certificate certificate, byte[] aaguid) {
        byte[] extension = certificate.getExtensionValue(OID_AAGUID);
        if (aaguid == null || aaguid.length == 0 || isAllZero(aaguid)) {
            if (extension != null) {
                throw new WebAuthnVerificationException(
                        "Packed attestation certificate must not contain an AAGUID "
                                + "extension when the authenticator AAGUID is zero");
            }
            return;
        }
        if (extension == null) {
            throw new WebAuthnVerificationException(
                    "Packed attestation certificate has no AAGUID extension");
        }
        if (!MessageDigest.isEqual(aaguidExtensionValue(extension), aaguid)) {
            throw new WebAuthnVerificationException(
                    "Packed attestation certificate AAGUID does not match the authenticator");
        }
    }

    // Extracts the 16 byte AAGUID from the DER encoded extension value
    private static byte[] aaguidExtensionValue(byte[] extension) {
        byte[] contents = unwrapOctetString(extension);
        byte[] value = contents.length == 16 ? contents : unwrapOctetString(contents);
        if (value.length != 16) {
            throw new WebAuthnVerificationException(
                    "Packed attestation certificate AAGUID extension is malformed");
        }
        return value;
    }

    // Unwraps one DER encoded OCTET STRING into its contents
    private static byte[] unwrapOctetString(byte[] der) {
        if (der.length < 2 || der[0] != 0x04) {
            throw new WebAuthnVerificationException(
                    "DER OCTET STRING is malformed in the attestation certificate");
        }
        int firstLengthByte = der[1] & 0xFF;
        int offset = 2;
        int length;
        if (firstLengthByte < 0x80) {
            length = firstLengthByte;
        } else {
            int count = firstLengthByte & 0x7F;
            if (count == 0 || count > 2 || der.length < 2 + count) {
                throw new WebAuthnVerificationException(
                        "DER OCTET STRING length is malformed in the attestation certificate");
            }
            length = 0;
            for (int index = 0; index < count; index++) {
                length = (length << 8) | (der[offset++] & 0xFF);
            }
        }
        if (length != der.length - offset) {
            throw new WebAuthnVerificationException(
                    "DER OCTET STRING length is inconsistent in the attestation certificate");
        }
        byte[] value = new byte[length];
        System.arraycopy(der, offset, value, 0, length);
        return value;
    }

    // Verifies the subject fields required of packed and U2F attestation certificates
    private static void verifyAttestationSubject(X509Certificate certificate) {
        List<Attribute> attributes = subjectAttributes(certificate);
        if (!hasAnyValue(attributes, "C")) {
            throw new WebAuthnVerificationException(
                    "Attestation certificate subject has no country");
        }
        if (!hasAnyValue(attributes, "O")) {
            throw new WebAuthnVerificationException(
                    "Attestation certificate subject has no organization");
        }
        if (!attributeValues(attributes, "OU").contains(ATTESTATION_OU)) {
            throw new WebAuthnVerificationException(
                    "Attestation certificate subject organizational unit is not "
                            + "'" + ATTESTATION_OU + "'");
        }
        if (!hasAnyValue(attributes, "CN")) {
            throw new WebAuthnVerificationException(
                    "Attestation certificate subject has no common name");
        }
    }

    private static List<Attribute> subjectAttributes(X509Certificate certificate) {
        try {
            LdapName name = new LdapName(
                    certificate.getSubjectX500Principal().getName(X500Principal.RFC2253));
            List<Attribute> attributes = new ArrayList<>();
            for (Rdn rdn : name.getRdns()) {
                Attributes values = rdn.toAttributes();
                NamingEnumeration<? extends Attribute> entries = values.getAll();
                while (entries.hasMore()) {
                    attributes.add(entries.next());
                }
            }
            return attributes;
        } catch (NamingException | IllegalArgumentException exception) {
            throw new WebAuthnVerificationException(
                    "Attestation certificate subject is malformed", exception);
        }
    }

    private static boolean hasAnyValue(List<Attribute> attributes, String type) {
        for (String value : attributeValues(attributes, type)) {
            if (!value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    // Collects all values of a subject attribute across RDNs
    private static List<String> attributeValues(List<Attribute> attributes, String type) {
        List<String> values = new ArrayList<>();
        for (Attribute attribute : attributes) {
            if (!type.equalsIgnoreCase(attribute.getID())) {
                continue;
            }
            try {
                NamingEnumeration<?> entries = attribute.getAll();
                while (entries.hasMore()) {
                    if (entries.next() instanceof String text) {
                        values.add(text);
                    }
                }
            } catch (NamingException exception) {
                throw new WebAuthnVerificationException(
                        "Attestation certificate subject is malformed", exception);
            }
        }
        return values;
    }

    // Requires the attestation certificate to be a non-CA certificate
    private static void verifyAttestationBasicConstraints(X509Certificate certificate) {
        if (certificate.getExtensionValue(OID_BASIC_CONSTRAINTS) == null) {
            throw new WebAuthnVerificationException(
                    "Attestation certificate has no basic constraints extension");
        }
        if (certificate.getBasicConstraints() != -1) {
            throw new WebAuthnVerificationException(
                    "Attestation certificate must not be a certificate authority");
        }
    }

    // Verifies the signatures that link each certificate to its issuer
    private static void verifyCertificateChain(List<X509Certificate> chain) {
        for (int index = 0; index < chain.size() - 1; index++) {
            X509Certificate certificate = chain.get(index);
            X509Certificate issuer = chain.get(index + 1);
            if (!certificate.getIssuerX500Principal()
                    .equals(issuer.getSubjectX500Principal())) {
                throw new WebAuthnVerificationException(
                        "Attestation certificate chain issuer does not match");
            }
            if (issuer.getBasicConstraints() == -1) {
                throw new WebAuthnVerificationException(
                        "Attestation certificate chain issuer is not a certificate authority");
            }
            try {
                certificate.verify(issuer.getPublicKey());
            } catch (GeneralSecurityException exception) {
                throw new WebAuthnVerificationException(
                        "Attestation certificate chain signature is invalid", exception);
            }
        }
    }

    private static List<X509Certificate> parseCertificateChain(Object value, String name) {
        if (!(value instanceof List<?> entries) || entries.isEmpty()) {
            throw new WebAuthnVerificationException(name + " x5c chain is missing");
        }
        CertificateFactory factory;
        try {
            factory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException exception) {
            throw new IllegalStateException("X.509 certificate factory is not available",
                    exception);
        }
        List<X509Certificate> chain = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            if (!(entry instanceof byte[] der) || der.length == 0) {
                throw new WebAuthnVerificationException(
                        name + " x5c entry is not a DER certificate");
            }
            try {
                chain.add((X509Certificate) factory.generateCertificate(
                        new ByteArrayInputStream(der)));
            } catch (CertificateException exception) {
                throw new WebAuthnVerificationException(
                        name + " x5c entry is not a valid X.509 certificate", exception);
            }
        }
        return chain;
    }

    // Builds the uncompressed U2F public key from the COSE EC2 key
    private static byte[] u2fPublicKey(CoseKey credentialKey) {
        byte[] x = fixedLength(credentialKey.x(), "x");
        byte[] y = fixedLength(credentialKey.y(), "y");
        byte[] key = new byte[1 + x.length + y.length];
        key[0] = U2F_UNCOMPRESSED_POINT;
        System.arraycopy(x, 0, key, 1, x.length);
        System.arraycopy(y, 0, key, 1 + x.length, y.length);
        return key;
    }

    // Left pads a P-256 coordinate to 32 bytes
    private static byte[] fixedLength(byte[] value, String name) {
        if (value == null || value.length == 0 || value.length > SHA256_LENGTH) {
            throw new WebAuthnVerificationException(
                    "FIDO U2F credential key coordinate " + name + " is malformed");
        }
        if (value.length == SHA256_LENGTH) {
            return value;
        }
        byte[] padded = new byte[SHA256_LENGTH];
        System.arraycopy(value, 0, padded, SHA256_LENGTH - value.length, value.length);
        return padded;
    }

    private static boolean isAllZero(byte[] value) {
        for (byte element : value) {
            if (element != 0) {
                return false;
            }
        }
        return true;
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] combined = new byte[left.length + right.length];
        System.arraycopy(left, 0, combined, 0, left.length);
        System.arraycopy(right, 0, combined, left.length, right.length);
        return combined;
    }

    private static int asInt(Object value, String name) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new WebAuthnVerificationException(name + " is missing");
    }

    private static byte[] asBytes(Object value, String name) {
        if (value instanceof byte[] bytes && bytes.length > 0) {
            return bytes;
        }
        throw new WebAuthnVerificationException(name + " is missing");
    }
}