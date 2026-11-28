package cn.tolim.pudans.iam.protocol.saml.signature;

import cn.tolim.pudans.iam.protocol.saml.exception.SamlErrorCodes;
import cn.tolim.pudans.iam.protocol.saml.exception.SamlProtocolException;
import cn.tolim.pudans.iam.protocol.saml.model.SamlProtocolConfig;
import cn.tolim.pudans.iam.protocol.saml.util.SamlXmlUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SAML_ASSERTION_NAMESPACE;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SAML_PROTOCOL_NAMESPACE;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.XML_SIGNATURE_NAMESPACE;


public class JdkSamlResponseSigner implements SamlResponseSigner {

    private static final XMLSignatureFactory SIGNATURE_FACTORY =
            XMLSignatureFactory.getInstance("DOM");

    @Override
    public String sign(String samlResponseXml, SamlProtocolConfig config) {
        Assert.hasText(samlResponseXml, "samlResponseXml must not be blank");
        Assert.notNull(config, "config must not be null");
        SamlSignatureSupport.AlgorithmSpec algorithm =
                SamlSignatureSupport.algorithm(config.getSigningAlgorithm());
        PrivateKey privateKey = SamlSignatureSupport.privateKey(config, algorithm);
        X509Certificate certificate = SamlSignatureSupport.certificate(config);
        try {
            Document document = SamlXmlUtils.parseDocument(samlResponseXml);
            Element root = document.getDocumentElement();
            Element target = selectTarget(root, config);
            String id = SamlSignatureSupport.id(target);
            if (StringUtils.isBlank(id)) {
                throw new SamlProtocolException(SamlErrorCodes.SIGNING_FAILED,
                        "SAML signing target has no ID attribute");
            }
            if (SamlXmlUtils.firstChild(target, XML_SIGNATURE_NAMESPACE, "Signature") != null) {
                throw new SamlProtocolException(SamlErrorCodes.SIGNING_FAILED,
                        "SAML document is already signed");
            }
            SamlSignatureSupport.markIdAttributes(document);
            Reference reference = SIGNATURE_FACTORY.newReference("#" + id,
                    SIGNATURE_FACTORY.newDigestMethod(algorithm.digestMethod(), null),
                    List.of(SIGNATURE_FACTORY.newTransform(Transform.ENVELOPED,
                                    (XMLStructure) null),
                            SIGNATURE_FACTORY.newTransform(CanonicalizationMethod.EXCLUSIVE,
                                    (XMLStructure) null)),
                    null, null);
            SignedInfo signedInfo = SIGNATURE_FACTORY.newSignedInfo(
                    SIGNATURE_FACTORY.newCanonicalizationMethod(
                            CanonicalizationMethod.EXCLUSIVE,
                            (C14NMethodParameterSpec) null),
                    SIGNATURE_FACTORY.newSignatureMethod(algorithm.signatureMethod(), null),
                    List.of(reference));
            KeyInfo keyInfo = SamlSignatureSupport.keyInfo(SIGNATURE_FACTORY, certificate);
            XMLSignature signature = SIGNATURE_FACTORY.newXMLSignature(signedInfo, keyInfo);
            DOMSignContext context = new DOMSignContext(privateKey, target);
            context.setDefaultNamespacePrefix("ds");
            signature.sign(context);
            repositionSignature(target);
            return SamlXmlUtils.serializeDocument(document);
        } catch (SamlProtocolException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SamlProtocolException(SamlErrorCodes.SIGNING_FAILED,
                    "Unable to sign the SAML response: " + exception.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, exception);
        }
    }

    // Sign the assertion when assertions are required, otherwise sign the response.
    private Element selectTarget(Element root, SamlProtocolConfig config) {
        boolean response = "Response".equals(root.getLocalName())
                && SAML_PROTOCOL_NAMESPACE.equals(root.getNamespaceURI());
        boolean assertion = "Assertion".equals(root.getLocalName())
                && SAML_ASSERTION_NAMESPACE.equals(root.getNamespaceURI());
        if (!response && !assertion) {
            throw new SamlProtocolException(SamlErrorCodes.XML_ERROR,
                    "SAML document is not a Response or Assertion");
        }
        if (response && config.getWantAssertionsSigned()) {
            Element assertionElement = SamlXmlUtils.firstChild(root,
                    SAML_ASSERTION_NAMESPACE, "Assertion");
            if (assertionElement != null) {
                return assertionElement;
            }
        }
        return root;
    }

    // Move the generated signature directly after Issuer to follow the SAML schema order.
    private void repositionSignature(Element target) {
        Element signature = SamlXmlUtils.firstChild(target, XML_SIGNATURE_NAMESPACE, "Signature");
        Element issuer = SamlXmlUtils.firstChild(target, SAML_ASSERTION_NAMESPACE, "Issuer");
        if (signature == null || issuer == null || signature.getPreviousSibling() == issuer) {
            return;
        }
        target.removeChild(signature);
        Node next = issuer.getNextSibling();
        if (next == null) {
            target.appendChild(signature);
        } else {
            target.insertBefore(signature, next);
        }
    }
}