package cn.tolim.pudans.iam.protocol.saml.signature;

import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlNameIdFormat;
import cn.tolim.pudans.iam.protocol.saml.exception.SamlErrorCodes;
import cn.tolim.pudans.iam.protocol.saml.exception.SamlProtocolException;
import cn.tolim.pudans.iam.protocol.saml.model.SamlAuthnRequest;
import cn.tolim.pudans.iam.protocol.saml.model.SamlProtocolConfig;
import cn.tolim.pudans.iam.protocol.saml.service.DefaultSamlProtocolService;
import cn.tolim.pudans.iam.protocol.saml.service.SamlEndpointResolver;
import cn.tolim.pudans.iam.protocol.saml.util.SamlXmlUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SAML_ASSERTION_NAMESPACE;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.XML_SIGNATURE_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;


class JdkSamlResponseSignerTests {

    private static final String PROTOCOL_NS = "urn:oasis:names:tc:SAML:2.0:protocol";

    private static KeyPair keyPair;
    private static X509Certificate certificate;
    private static String certificatePem;
    private static String certificateBase64;
    private static String privateKeyPem;
    private static X509Certificate otherCertificate;
    private static String otherCertificatePem;

    private final JdkSamlResponseSigner signer = new JdkSamlResponseSigner();
    private final JdkSamlResponseVerifier verifier = new JdkSamlResponseVerifier();

    private final SamlEndpointResolver resolver = new SamlEndpointResolver() {
        @Override
        public String idpEntityId(String appCode) {
            return "https://idp.test/v1/authorize/saml/" + appCode;
        }

        @Override
        public String metadataUrl(String appCode) {
            return idpEntityId(appCode) + "/metadata";
        }

        @Override
        public String ssoUrl(String appCode) {
            return idpEntityId(appCode) + "/sso";
        }

        @Override
        public String sloUrl(String appCode) {
            return idpEntityId(appCode) + "/slo";
        }
    };

    @BeforeAll
    static void createKeys() throws Exception {
        keyPair = createKeyPair();
        certificate = createCertificate(keyPair, "CN=idp-test", BigInteger.ONE);
        certificatePem = pem("CERTIFICATE", certificate.getEncoded());
        certificateBase64 = Base64.getEncoder().encodeToString(certificate.getEncoded());
        privateKeyPem = pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
        KeyPair other = createKeyPair();
        otherCertificate = createCertificate(other, "CN=other-idp", BigInteger.TWO);
        otherCertificatePem = pem("CERTIFICATE", otherCertificate.getEncoded());
    }

    private static KeyPair createKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate createCertificate(KeyPair pair, String subject,
                                                     BigInteger serial) throws Exception {
        X500Name name = new X500Name(subject);
        Date notBefore = Date.from(Instant.now().minusSeconds(86400));
        Date notAfter = Date.from(Instant.now().plusSeconds(315360000L));
        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(name, serial,
                notBefore, notAfter, name,
                SubjectPublicKeyInfo.getInstance(pair.getPublic().getEncoded()));
        ContentSigner certificateSigner = new JcaContentSignerBuilder("SHA256withRSA")
                .build(pair.getPrivate());
        byte[] encoded = builder.build(certificateSigner).getEncoded();
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(encoded));
    }

    private static String pem(String type, byte[] value) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(value)
                + "\n-----END " + type + "-----";
    }

    private static SamlProtocolConfig config(boolean wantAssertionsSigned) {
        return SamlProtocolConfig.builder()
                .appCode("app-1")
                .entityId("https://sp.test")
                .assertionConsumerServiceUrl("https://sp.test/acs")
                .nameIdFormat(SamlNameIdFormat.EMAIL)
                .wantAssertionsSigned(wantAssertionsSigned)
                .signingCertificate(certificatePem)
                .signingPrivateKey(privateKeyPem)
                .build();
    }

    private static SamlAuthnRequest authnRequest() {
        return SamlAuthnRequest.builder()
                .id("_request-1")
                .issuer("https://sp.test")
                .assertionConsumerServiceUrl("https://sp.test/acs")
                .nameIdFormat("urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress")
                .build();
    }

    private static UserDetails user() {
        UserDetails user = new UserDetails("user-1", "alice", UserType.USER, true, true, true,
                true, List.of());
        user.setEmail("alice@example.test");
        user.setFullName("Alice");
        return user;
    }

    private String signedResponse(SamlProtocolConfig config) {
        DefaultSamlProtocolService service = new DefaultSamlProtocolService(signer, resolver);
        return service.buildSignedResponse(config, authnRequest(), user());
    }

    private String unsignedResponse() {
        DefaultSamlProtocolService service = new DefaultSamlProtocolService(
                (xml, config) -> xml, resolver);
        return service.buildSignedResponse(config(true), authnRequest(), user());
    }

    @Test
    void signsAndVerifiesAssertion() {
        SamlProtocolConfig config = config(true);

        String response = signedResponse(config);

        assertThat(response).contains("<ds:Signature");
        assertThat(response).contains("<ds:X509Certificate>");
        Element assertion = assertionElement(response);
        Element signature = SamlXmlUtils.firstChild(assertion, XML_SIGNATURE_NAMESPACE,
                "Signature");
        assertThat(signature).isNotNull();
        Element issuer = SamlXmlUtils.firstChild(assertion, SAML_ASSERTION_NAMESPACE, "Issuer");
        assertThat(signature.getPreviousSibling()).isSameAs(issuer);
        assertThat(response).contains("Reference URI=\"#" + assertion.getAttribute("ID") + "\"");
        assertThat(SamlXmlUtils.firstChild(responseRoot(response), XML_SIGNATURE_NAMESPACE,
                "Signature")).isNull();
        assertDoesNotThrow(() -> verifier.verify(response, config));
    }

    @Test
    void signsResponseWhenAssertionsAreNotRequired() {
        SamlProtocolConfig config = config(false);

        String response = signedResponse(config);

        assertThat(SamlXmlUtils.firstChild(responseRoot(response), XML_SIGNATURE_NAMESPACE,
                "Signature")).isNotNull();
        assertThat(SamlXmlUtils.firstChild(assertionElement(response), XML_SIGNATURE_NAMESPACE,
                "Signature")).isNull();
        assertDoesNotThrow(() -> verifier.verify(response, config));
    }

    @Test
    void signsAndVerifiesStandaloneAssertion() {
        SamlProtocolConfig config = config(true);
        String assertion = "<saml:Assertion xmlns:saml=\"" + SAML_ASSERTION_NAMESPACE
                + "\" ID=\"_assertion-1\" Version=\"2.0\" IssueInstant=\"2026-01-01T00:00:00Z\">"
                + "<saml:Issuer>https://idp.test</saml:Issuer>"
                + "<saml:Subject><saml:NameID>alice@example.test</saml:NameID></saml:Subject>"
                + "</saml:Assertion>";

        String signed = signer.sign(assertion, config);

        assertThat(signed).contains("<ds:Signature");
        assertDoesNotThrow(() -> verifier.verify(signed, config));
    }

    @Test
    void usesConfiguredSignatureAlgorithm() {
        SamlProtocolConfig config = SamlProtocolConfig.builder()
                .appCode("app-1")
                .entityId("https://sp.test")
                .assertionConsumerServiceUrl("https://sp.test/acs")
                .signingAlgorithm("RSA_SHA512")
                .signingCertificate(certificatePem)
                .signingPrivateKey(privateKeyPem)
                .build();

        String response = signedResponse(config);

        assertThat(response).contains("http://www.w3.org/2001/04/xmldsig-more#rsa-sha512");
        assertDoesNotThrow(() -> verifier.verify(response, config));
    }

    @Test
    void rejectsTamperedAssertionContent() {
        SamlProtocolConfig config = config(true);
        String response = signedResponse(config);
        String tampered = response.replace("alice@example.test", "eve@example.test");
        assertThat(tampered).isNotEqualTo(response);

        SamlProtocolException exception = assertThrows(SamlProtocolException.class,
                () -> verifier.verify(tampered, config));

        assertThat(exception.getErrorCode()).isEqualTo(SamlErrorCodes.SIGNATURE_INVALID);
    }

    @Test
    void rejectsTamperedSignatureValue() {
        SamlProtocolConfig config = config(true);
        String response = signedResponse(config);
        String signatureValue = extractSignatureValue(response);
        assertThat(signatureValue).isNotBlank();
        String first = signatureValue.substring(0, 1);
        String replacement = "A".equals(first) ? "B" : "A";
        String tampered = response.replace(signatureValue,
                replacement + signatureValue.substring(1));

        SamlProtocolException exception = assertThrows(SamlProtocolException.class,
                () -> verifier.verify(tampered, config));

        assertThat(exception.getErrorCode()).isEqualTo(SamlErrorCodes.SIGNATURE_INVALID);
    }

    @Test
    void rejectsUnsignedResponse() {
        String response = unsignedResponse();

        SamlProtocolException exception = assertThrows(SamlProtocolException.class,
                () -> verifier.verify(response, config(true)));

        assertThat(exception.getErrorCode()).isEqualTo(SamlErrorCodes.SIGNATURE_MISSING);
    }

    @Test
    void rejectsSignatureFromAnotherCertificate() {
        SamlProtocolConfig config = config(true);
        String response = signedResponse(config);
        SamlProtocolConfig otherConfig = SamlProtocolConfig.builder()
                .signingCertificate(otherCertificatePem)
                .build();

        SamlProtocolException exception = assertThrows(SamlProtocolException.class,
                () -> verifier.verify(response, otherConfig));

        assertThat(exception.getErrorCode()).isEqualTo(SamlErrorCodes.SIGNATURE_INVALID);
    }

    @Test
    void rejectsSigningWithoutPrivateKey() {
        SamlProtocolConfig config = SamlProtocolConfig.builder()
                .appCode("app-1")
                .signingCertificate(certificatePem)
                .build();

        SamlProtocolException exception = assertThrows(SamlProtocolException.class,
                () -> signer.sign(unsignedResponse(), config));

        assertThat(exception.getErrorCode()).isEqualTo(SamlErrorCodes.CONFIG_ERROR);
    }

    @Test
    void rejectsUnknownSigningAlgorithm() {
        SamlProtocolConfig config = SamlProtocolConfig.builder()
                .appCode("app-1")
                .signingAlgorithm("rot13")
                .signingCertificate(certificatePem)
                .signingPrivateKey(privateKeyPem)
                .build();

        SamlProtocolException exception = assertThrows(SamlProtocolException.class,
                () -> signer.sign(unsignedResponse(), config));

        assertThat(exception.getErrorCode()).isEqualTo(SamlErrorCodes.CONFIG_ERROR);
    }

    @Test
    void metadataPublishesSigningCertificate() {
        DefaultSamlProtocolService service = new DefaultSamlProtocolService(signer, resolver);

        String metadata = service.generateMetadata(config(true));

        assertThat(metadata).contains("<ds:X509Certificate>");
        assertThat(metadata).contains(certificateBase64.substring(0, 32));
    }

    private static Element responseRoot(String response) {
        Document document = SamlXmlUtils.parseDocument(response);
        return document.getDocumentElement();
    }

    private static Element assertionElement(String response) {
        return SamlXmlUtils.firstChild(responseRoot(response), SAML_ASSERTION_NAMESPACE,
                "Assertion");
    }

    private static String extractSignatureValue(String response) {
        int start = response.indexOf("<ds:SignatureValue>");
        int end = response.indexOf("</ds:SignatureValue>");
        assertThat(start).isGreaterThan(-1);
        assertThat(end).isGreaterThan(start);
        return response.substring(start + "<ds:SignatureValue>".length(), end);
    }
}