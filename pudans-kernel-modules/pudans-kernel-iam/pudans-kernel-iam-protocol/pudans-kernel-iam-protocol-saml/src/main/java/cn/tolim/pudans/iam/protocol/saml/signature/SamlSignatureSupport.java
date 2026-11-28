package cn.tolim.pudans.iam.protocol.saml.signature;

import cn.tolim.pudans.iam.protocol.saml.exception.SamlErrorCodes;
import cn.tolim.pudans.iam.protocol.saml.exception.SamlProtocolException;
import cn.tolim.pudans.iam.protocol.saml.model.SamlProtocolConfig;
import cn.tolim.pudans.iam.protocol.saml.util.SamlXmlUtils;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


final class SamlSignatureSupport {

    private static final AlgorithmSpec DEFAULT_ALGORITHM = new AlgorithmSpec(
            SignatureMethod.RSA_SHA256, DigestMethod.SHA256, "RSA");

    private static final Map<String, AlgorithmSpec> ALGORITHMS = createAlgorithms();

    private SamlSignatureSupport() {
    }

    // Resolve the configured signature algorithm or fail with a configuration error.
    static AlgorithmSpec algorithm(String configured) {
        if (StringUtils.isBlank(configured)) {
            return DEFAULT_ALGORITHM;
        }
        AlgorithmSpec spec = ALGORITHMS.get(normalize(configured));
        if (spec == null) {
            throw new SamlProtocolException(SamlErrorCodes.CONFIG_ERROR,
                    "Unsupported SAML signing algorithm: " + configured);
        }
        return spec;
    }

    // Resolve the signing certificate from the protocol configuration.
    static X509Certificate certificate(SamlProtocolConfig config) {
        return certificate(config.getSigningCertificate());
    }

    // Parse a PEM or base64 X.509 certificate.
    static X509Certificate certificate(String certificate) {
        if (StringUtils.isBlank(certificate)) {
            throw new SamlProtocolException(SamlErrorCodes.CONFIG_ERROR,
                    "SAML signing certificate is not configured");
        }
        try {
            byte[] encoded = Base64.getDecoder().decode(
                    SamlXmlUtils.normalizePem(certificate));
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(encoded));
        } catch (Exception exception) {
            throw new SamlProtocolException(SamlErrorCodes.CONFIG_ERROR,
                    "SAML signing certificate is invalid: " + exception.getMessage(), exception);
        }
    }

    // Parse the PKCS#8 private key from the protocol configuration.
    static PrivateKey privateKey(SamlProtocolConfig config, AlgorithmSpec algorithm) {
        if (StringUtils.isBlank(config.getSigningPrivateKey())) {
            throw new SamlProtocolException(SamlErrorCodes.CONFIG_ERROR,
                    "SAML signing private key is not configured");
        }
        try {
            byte[] encoded = Base64.getDecoder().decode(
                    SamlXmlUtils.normalizePem(config.getSigningPrivateKey()));
            return KeyFactory.getInstance(algorithm.keyAlgorithm())
                    .generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new SamlProtocolException(SamlErrorCodes.CONFIG_ERROR,
                    "SAML signing private key is invalid: " + exception.getMessage(), exception);
        }
    }

    // Build KeyInfo carrying the signing certificate.
    static KeyInfo keyInfo(XMLSignatureFactory factory, X509Certificate certificate) {
        KeyInfoFactory keyInfoFactory = factory.getKeyInfoFactory();
        return keyInfoFactory.newKeyInfo(List.of(
                keyInfoFactory.newX509Data(List.of(certificate))));
    }

    // Register every ID attribute in the document so same-document references resolve.
    static void markIdAttributes(Document document) {
        markIdAttributes(document.getDocumentElement());
    }

    private static void markIdAttributes(Element element) {
        if (element == null) {
            return;
        }
        if (StringUtils.isNotBlank(element.getAttribute("ID"))) {
            element.setIdAttribute("ID", true);
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                markIdAttributes((Element) node);
            }
        }
    }

    // Reject documents that reuse an ID value to defeat signature wrapping checks.
    static void rejectDuplicateIds(Element root) {
        Map<String, Integer> ids = new HashMap<>();
        collectIds(root, ids);
        boolean duplicate = ids.values().stream().anyMatch(count -> count > 1);
        if (duplicate) {
            throw new SamlProtocolException(SamlErrorCodes.SIGNATURE_INVALID,
                    "SAML document contains duplicate ID attributes");
        }
    }

    private static void collectIds(Element element, Map<String, Integer> ids) {
        if (element == null) {
            return;
        }
        String id = element.getAttribute("ID");
        if (StringUtils.isNotBlank(id)) {
            ids.merge(id, 1, Integer::sum);
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                collectIds((Element) node, ids);
            }
        }
    }

    // Return the non-blank ID of an element.
    static String id(Element element) {
        String id = element.getAttribute("ID");
        return StringUtils.isBlank(id) ? null : id;
    }

    private static Map<String, AlgorithmSpec> createAlgorithms() {
        Map<String, AlgorithmSpec> algorithms = new HashMap<>();
        register(algorithms, SignatureMethod.RSA_SHA1, DigestMethod.SHA1, "RSA",
                "RS1", "RSASHA1", "SHA1WITHRSA");
        register(algorithms, SignatureMethod.RSA_SHA224, DigestMethod.SHA224, "RSA",
                "RS224", "RSASHA224", "SHA224WITHRSA");
        register(algorithms, SignatureMethod.RSA_SHA256, DigestMethod.SHA256, "RSA",
                "RS256", "RSASHA256", "SHA256WITHRSA");
        register(algorithms, SignatureMethod.RSA_SHA384, DigestMethod.SHA384, "RSA",
                "RS384", "RSASHA384", "SHA384WITHRSA");
        register(algorithms, SignatureMethod.RSA_SHA512, DigestMethod.SHA512, "RSA",
                "RS512", "RSASHA512", "SHA512WITHRSA");
        register(algorithms, SignatureMethod.ECDSA_SHA1, DigestMethod.SHA1, "EC",
                "ECDSASHA1", "SHA1WITHECDSA");
        register(algorithms, SignatureMethod.ECDSA_SHA224, DigestMethod.SHA224, "EC",
                "ECDSASHA224", "SHA224WITHECDSA");
        register(algorithms, SignatureMethod.ECDSA_SHA256, DigestMethod.SHA256, "EC",
                "ES256", "ECDSASHA256", "SHA256WITHECDSA");
        register(algorithms, SignatureMethod.ECDSA_SHA384, DigestMethod.SHA384, "EC",
                "ES384", "ECDSASHA384", "SHA384WITHECDSA");
        register(algorithms, SignatureMethod.ECDSA_SHA512, DigestMethod.SHA512, "EC",
                "ES512", "ECDSASHA512", "SHA512WITHECDSA");
        return Map.copyOf(algorithms);
    }

    private static void register(Map<String, AlgorithmSpec> algorithms, String signatureMethod,
                                 String digestMethod, String keyAlgorithm, String... aliases) {
        AlgorithmSpec spec = new AlgorithmSpec(signatureMethod, digestMethod, keyAlgorithm);
        algorithms.put(normalize(signatureMethod), spec);
        for (String alias : aliases) {
            algorithms.put(normalize(alias), spec);
        }
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
    }

    // Signature and digest algorithm pair plus the JCA key algorithm.
    record AlgorithmSpec(String signatureMethod, String digestMethod, String keyAlgorithm) {
    }
}