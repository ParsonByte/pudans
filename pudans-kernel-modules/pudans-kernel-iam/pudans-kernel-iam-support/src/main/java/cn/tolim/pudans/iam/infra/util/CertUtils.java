package cn.tolim.pudans.iam.infra.util;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

public final class CertUtils {
    private CertUtils() {
    }

    public static X500Name getX500Name(String cn, String organization, String locality,
                                       String state, String country, String organizationalUnit) {
        X500NameBuilder builder = new X500NameBuilder(BCStyle.INSTANCE);
        if (cn != null) {
            builder.addRDN(BCStyle.CN, cn);
        }
        if (organization != null) {
            builder.addRDN(BCStyle.O, organization);
        }
        if (locality != null) {
            builder.addRDN(BCStyle.L, locality);
        }
        if (state != null) {
            builder.addRDN(BCStyle.ST, state);
        }
        if (country != null) {
            builder.addRDN(BCStyle.C, country);
        }
        if (organizationalUnit != null) {
            builder.addRDN(BCStyle.OU, organizationalUnit);
        }
        return builder.build();
    }

    public static String getCertificate(X500Name subject, X500Name issuer, BigInteger serial,
                                        Date notBefore, Date notAfter, PublicKey publicKey,
                                        PrivateKey privateKey) throws Exception {
        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(issuer, serial, notBefore,
                notAfter, subject, SubjectPublicKeyInfo.getInstance(publicKey.getEncoded()));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);
        return encodePem(builder.build(signer).toASN1Structure().getEncoded());
    }

    public static PrivateKey readPrivateKey(String pem, String password) throws IOException {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object object = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (object instanceof PEMKeyPair pair) {
                return converter.getKeyPair(pair).getPrivate();
            }
            if (object instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo info) {
                return converter.getPrivateKey(info);
            }
            throw new IOException("不支持的私钥格式");
        }
    }

    public static PublicKey readPublicKey(String pem, String password) throws IOException {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object object = parser.readObject();
            if (object instanceof org.bouncycastle.asn1.x509.SubjectPublicKeyInfo info) {
                return new JcaPEMKeyConverter().getPublicKey(info);
            }
            if (object instanceof X509Certificate certificate) {
                return certificate.getPublicKey();
            }
            throw new IOException("不支持的公钥格式");
        }
    }

    public static X509Certificate getCertificate(byte[] value) throws CertificateException {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(value));
    }

    public static X509Certificate loadCertFromString(String value) {
        try {
            return getCertificate(keyCleanup(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (CertificateException exception) {
            throw new IllegalArgumentException("证书无效", exception);
        }
    }

    public static X509Certificate loadCertFromInputStream(java.io.InputStream inputStream)
            throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(inputStream);
    }

    public static RSAPrivateKey getPrivateKey(byte[] value, String algorithm)
            throws java.security.NoSuchAlgorithmException,
            java.security.spec.InvalidKeySpecException {
        return (RSAPrivateKey) KeyFactory.getInstance(algorithm)
                .generatePrivate(new PKCS8EncodedKeySpec(value));
    }

    public static String encodePem(PrivateKey privateKey) {
        return encodePem((Object) privateKey);
    }

    public static String encodePem(PublicKey publicKey) {
        return encodePem((Object) publicKey);
    }

    public static String encodePem(Certificate certificate) {
        return encodePem((Object) certificate);
    }

    public static String getCertificate(Certificate certificate) {
        return encodePem(certificate);
    }

    public static String getSubjectDn(X509Certificate certificate) {
        return certificate.getSubjectX500Principal().getName();
    }

    public static String getIssuerDn(X509Certificate certificate) {
        return certificate.getIssuerX500Principal().getName();
    }

    public static String getNotAfter(X509Certificate certificate) {
        return certificate.getNotAfter().toInstant().toString();
    }

    public static String getPin(X509Certificate certificate) {
        try {
            byte[] encoded = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(certificate.getEncoded());
            StringBuilder pin = new StringBuilder();
            for (byte value : encoded) {
                pin.append(String.format("%02X", value));
            }
            return pin.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("计算证书指纹失败", exception);
        }
    }

    public static String keyCleanup(String value) {
        return value == null ? null
                : value.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "").replaceAll("\\s", "");
    }

    public static String toPem(String type, byte[] value) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(value);
        return "-----BEGIN " + type + "-----\n" + encoded + "\n-----END " + type + "-----";
    }

    private static String encodePem(Object value) {
        try (StringWriter writer = new StringWriter();
             JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(value);
            pemWriter.flush();
            return writer.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("PEM 编码失败", exception);
        }
    }
}
