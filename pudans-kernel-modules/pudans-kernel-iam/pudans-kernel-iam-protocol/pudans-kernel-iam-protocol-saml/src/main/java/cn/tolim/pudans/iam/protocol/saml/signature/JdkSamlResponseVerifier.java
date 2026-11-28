package cn.tolim.pudans.iam.protocol.saml.signature;

import cn.tolim.pudans.iam.protocol.saml.exception.SamlErrorCodes;
import cn.tolim.pudans.iam.protocol.saml.exception.SamlProtocolException;
import cn.tolim.pudans.iam.protocol.saml.model.SamlProtocolConfig;
import cn.tolim.pudans.iam.protocol.saml.util.SamlXmlUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SAML_ASSERTION_NAMESPACE;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SAML_PROTOCOL_NAMESPACE;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.XML_SIGNATURE_NAMESPACE;


public class JdkSamlResponseVerifier implements SamlResponseVerifier {

    private static final XMLSignatureFactory SIGNATURE_FACTORY =
            XMLSignatureFactory.getInstance("DOM");

    private static final String SECURE_VALIDATION = "org.jcp.xml.dsig.secureValidation";

    @Override
    public void verify(String samlResponseXml, SamlProtocolConfig config) {
        Assert.notNull(config, "config must not be null");
        verify(samlResponseXml, config.getSigningCertificate());
    }

    // Verify against a PEM or base64 X.509 certificate.
    public void verify(String samlResponseXml, String signingCertificate) {
        Assert.hasText(samlResponseXml, "samlResponseXml must not be blank");
        X509Certificate certificate = SamlSignatureSupport.certificate(signingCertificate);
        try {
            Document document = SamlXmlUtils.parseDocument(samlResponseXml);
            Element root = document.getDocumentElement();
            List<Element> targets = signatureTargets(root);
            SamlSignatureSupport.rejectDuplicateIds(root);
            SamlSignatureSupport.markIdAttributes(document);
            boolean signed = false;
            for (Element target : targets) {
                Element signatureElement = SamlXmlUtils.firstChild(target,
                        XML_SIGNATURE_NAMESPACE, "Signature");
                if (signatureElement == null) {
                    continue;
                }
                signed = true;
                if (verifySignature(signatureElement, target, certificate)) {
                    return;
                }
            }
            if (!signed) {
                throw new SamlProtocolException(SamlErrorCodes.SIGNATURE_MISSING,
                        "SAML document does not contain an XML signature");
            }
            throw new SamlProtocolException(SamlErrorCodes.SIGNATURE_INVALID,
                    "SAML XML signature is not valid");
        } catch (SamlProtocolException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SamlProtocolException(SamlErrorCodes.SIGNATURE_INVALID,
                    "Unable to verify the SAML XML signature: " + exception.getMessage(),
                    exception);
        }
    }

    // Return the response and assertion elements that may carry an enveloped signature.
    private List<Element> signatureTargets(Element root) {
        boolean response = "Response".equals(root.getLocalName())
                && SAML_PROTOCOL_NAMESPACE.equals(root.getNamespaceURI());
        if (response) {
            List<Element> targets = new ArrayList<>();
            targets.add(root);
            targets.addAll(SamlXmlUtils.children(root, SAML_ASSERTION_NAMESPACE, "Assertion"));
            return targets;
        }
        boolean assertion = "Assertion".equals(root.getLocalName())
                && SAML_ASSERTION_NAMESPACE.equals(root.getNamespaceURI());
        if (assertion) {
            return List.of(root);
        }
        throw new SamlProtocolException(SamlErrorCodes.XML_ERROR,
                "SAML document is not a Response or Assertion");
    }

    // Validate one signature and require every reference to cover the containing element.
    private boolean verifySignature(Element signatureElement, Element target,
                                    X509Certificate certificate) throws Exception {
        String id = SamlSignatureSupport.id(target);
        if (StringUtils.isBlank(id)) {
            return false;
        }
        DOMValidateContext context = new DOMValidateContext(certificate.getPublicKey(),
                signatureElement);
        context.setProperty(SECURE_VALIDATION, Boolean.TRUE);
        XMLSignature signature = SIGNATURE_FACTORY.unmarshalXMLSignature(context);
        List<Reference> references = signature.getSignedInfo().getReferences();
        if (references.isEmpty()) {
            return false;
        }
        for (Reference reference : references) {
            if (!("#" + id).equals(reference.getURI())) {
                return false;
            }
        }
        return signature.validate(context);
    }
}