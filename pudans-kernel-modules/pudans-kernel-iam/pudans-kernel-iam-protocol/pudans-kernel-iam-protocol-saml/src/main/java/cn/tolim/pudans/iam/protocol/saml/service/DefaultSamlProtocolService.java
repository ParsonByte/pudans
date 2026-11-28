package cn.tolim.pudans.iam.protocol.saml.service;

import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlBindingType;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlNameIdFormat;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants;
import cn.tolim.pudans.iam.protocol.saml.exception.SamlErrorCodes;
import cn.tolim.pudans.iam.protocol.saml.exception.SamlProtocolException;
import cn.tolim.pudans.iam.protocol.saml.model.SamlAuthnRequest;
import cn.tolim.pudans.iam.protocol.saml.model.SamlProtocolConfig;
import cn.tolim.pudans.iam.protocol.saml.signature.SamlResponseSigner;
import cn.tolim.pudans.iam.protocol.saml.util.SamlAttributeResolver;
import cn.tolim.pudans.iam.protocol.saml.util.SamlXmlUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SAML_ASSERTION_NAMESPACE;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SAML_METADATA_NAMESPACE;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SAML_PROTOCOL_NAMESPACE;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.XML_SIGNATURE_NAMESPACE;


public class DefaultSamlProtocolService implements SamlProtocolService {

    private final SamlResponseSigner responseSigner;
    private final SamlEndpointResolver endpointResolver;

    public DefaultSamlProtocolService(SamlResponseSigner responseSigner,
                                      SamlEndpointResolver endpointResolver) {
        Assert.notNull(responseSigner, "responseSigner must not be null");
        Assert.notNull(endpointResolver, "endpointResolver must not be null");
        this.responseSigner = responseSigner;
        this.endpointResolver = endpointResolver;
    }

    @Override
    public String generateMetadata(SamlProtocolConfig config) {
        String appCode = config.getAppCode();
        String entityId = endpointResolver.idpEntityId(appCode);
        String ssoUrl = endpointResolver.ssoUrl(appCode);
        String sloUrl = endpointResolver.sloUrl(appCode);
        String certificate = SamlXmlUtils.normalizeCertificate(config.getSigningCertificate());
        StringBuilder xml = new StringBuilder(2048);
        xml.append("<EntityDescriptor xmlns=\"").append(SAML_METADATA_NAMESPACE)
                .append("\" entityID=\"").append(SamlXmlUtils.escapeXml(entityId)).append("\">");
        xml.append("<IDPSSODescriptor protocolSupportEnumeration=\"")
                .append(SAML_PROTOCOL_NAMESPACE).append("\" WantAuthnRequestsSigned=\"")
                .append(config.getWantAuthnRequestsSigned()).append("\">");
        if (StringUtils.isNotBlank(certificate)) {
            xml.append("<KeyDescriptor use=\"signing\"><ds:KeyInfo xmlns:ds=\"")
                    .append(XML_SIGNATURE_NAMESPACE).append("\"><ds:X509Data><ds:X509Certificate>")
                    .append(SamlXmlUtils.escapeXml(certificate))
                    .append("</ds:X509Certificate></ds:X509Data></ds:KeyInfo></KeyDescriptor>");
        }
        xml.append("<NameIDFormat>").append(config.getNameIdFormat().getCode())
                .append("</NameIDFormat>");
        xml.append("<SingleSignOnService Binding=\"")
                .append(SamlBindingType.REDIRECT.getCode()).append("\" Location=\"")
                .append(SamlXmlUtils.escapeXml(ssoUrl)).append("\"/>");
        xml.append("<SingleSignOnService Binding=\"")
                .append(SamlBindingType.POST.getCode()).append("\" Location=\"")
                .append(SamlXmlUtils.escapeXml(ssoUrl)).append("\"/>");
        xml.append("<SingleLogoutService Binding=\"")
                .append(SamlBindingType.REDIRECT.getCode()).append("\" Location=\"")
                .append(SamlXmlUtils.escapeXml(sloUrl)).append("\"/>");
        xml.append("<SingleLogoutService Binding=\"")
                .append(SamlBindingType.POST.getCode()).append("\" Location=\"")
                .append(SamlXmlUtils.escapeXml(sloUrl)).append("\"/>");
        xml.append("</IDPSSODescriptor></EntityDescriptor>");
        return xml.toString();
    }

    @Override
    public SamlAuthnRequest parseAuthnRequest(String samlRequest, SamlBindingType bindingType) {
        SamlBindingType binding = Objects.requireNonNullElse(bindingType, SamlBindingType.REDIRECT);
        String xml = binding == SamlBindingType.REDIRECT
                ? SamlXmlUtils.decodeRedirectBinding(samlRequest)
                : SamlXmlUtils.decodePostBinding(samlRequest);
        Document document = SamlXmlUtils.parseDocument(xml);
        Element root = document.getDocumentElement();
        if (!"AuthnRequest".equals(root.getLocalName())
                || !SAML_PROTOCOL_NAMESPACE.equals(root.getNamespaceURI())) {
            throw new SamlProtocolException(SamlErrorCodes.INVALID_REQUEST,
                    "SAMLRequest is not a SAML 2.0 AuthnRequest");
        }
        String id = root.getAttribute("ID");
        if (StringUtils.isBlank(id)) {
            throw new SamlProtocolException(SamlErrorCodes.INVALID_REQUEST,
                    "AuthnRequest ID is missing");
        }
        Element issuerElement = SamlXmlUtils.firstChild(root, SAML_ASSERTION_NAMESPACE, "Issuer");
        String issuer = SamlXmlUtils.text(issuerElement);
        if (StringUtils.isBlank(issuer)) {
            throw new SamlProtocolException(SamlErrorCodes.INVALID_REQUEST,
                    "AuthnRequest issuer is missing");
        }
        Element nameIdPolicy = SamlXmlUtils.firstChild(root, SAML_PROTOCOL_NAMESPACE,
                "NameIDPolicy");
        return SamlAuthnRequest.builder()
                .id(id)
                .issuer(issuer)
                .assertionConsumerServiceUrl(root.getAttribute("AssertionConsumerServiceURL"))
                .destination(root.getAttribute("Destination"))
                .protocolBinding(root.getAttribute("ProtocolBinding"))
                .nameIdFormat(nameIdPolicy == null ? null : nameIdPolicy.getAttribute("Format"))
                .issueInstant(root.getAttribute("IssueInstant"))
                .forceAuthn(Boolean.valueOf(root.getAttribute("ForceAuthn")))
                .isPassive(Boolean.valueOf(root.getAttribute("IsPassive")))
                .build();
    }

    @Override
    public String buildSignedResponse(SamlProtocolConfig config, SamlAuthnRequest authnRequest,
                                      UserDetails user) {
        Assert.notNull(config, "config must not be null");
        Assert.notNull(authnRequest, "authnRequest must not be null");
        Assert.notNull(user, "user must not be null");
        String assertionConsumerServiceUrl = StringUtils.defaultIfBlank(
                authnRequest.getAssertionConsumerServiceUrl(),
                config.getAssertionConsumerServiceUrl());
        if (StringUtils.isBlank(assertionConsumerServiceUrl)) {
            throw new SamlProtocolException(SamlErrorCodes.CONFIG_ERROR,
                    "Assertion consumer service URL is not configured");
        }
        String responseXml = buildResponseXml(config, authnRequest, user,
                assertionConsumerServiceUrl);
        return responseSigner.sign(responseXml, config);
    }

    private String buildResponseXml(SamlProtocolConfig config, SamlAuthnRequest authnRequest,
                                    UserDetails user, String assertionConsumerServiceUrl) {
        Instant now = Instant.now();
        Duration skew = config.getClockSkew();
        Instant notBefore = now.minus(skew);
        Instant notOnOrAfter = now.plus(config.getAssertionTimeToLive()).plus(skew);
        String issueInstant = DateTimeFormatter.ISO_INSTANT.format(now);
        String responseId = SamlXmlUtils.newId();
        String assertionId = SamlXmlUtils.newId();
        String sessionIndex = SamlXmlUtils.newId();
        String recipient = SamlXmlUtils.escapeXml(assertionConsumerServiceUrl);
        String audience = SamlXmlUtils.escapeXml(StringUtils.defaultIfBlank(
                config.getEntityId(), authnRequest.getIssuer()));
        String nameIdFormat = StringUtils.defaultIfBlank(authnRequest.getNameIdFormat(),
                config.getNameIdFormat().getCode());
        String nameId = SamlXmlUtils.escapeXml(resolveNameId(config, user));
        Map<String, String> attributes = SamlAttributeResolver.resolve(user,
                config.getAttributeMappings());

        StringBuilder xml = new StringBuilder(4096);
        xml.append("<samlp:Response xmlns:samlp=\"").append(SAML_PROTOCOL_NAMESPACE)
                .append("\" xmlns:saml=\"").append(SAML_ASSERTION_NAMESPACE)
                .append("\" ID=\"").append(responseId)
                .append("\" Version=\"2.0\" IssueInstant=\"").append(issueInstant)
                .append("\" Destination=\"").append(recipient)
                .append("\" InResponseTo=\"").append(SamlXmlUtils.escapeXml(authnRequest.getId()))
                .append("\">");
        xml.append("<saml:Issuer>")
                .append(SamlXmlUtils.escapeXml(endpointResolver.idpEntityId(config.getAppCode())))
                .append("</saml:Issuer>");
        xml.append("<samlp:Status><samlp:StatusCode Value=\"")
                .append(SamlProtocolConstants.STATUS_SUCCESS)
                .append("\"/></samlp:Status>");
        xml.append("<saml:Assertion ID=\"").append(assertionId)
                .append("\" Version=\"2.0\" IssueInstant=\"").append(issueInstant).append("\">");
        xml.append("<saml:Issuer>")
                .append(SamlXmlUtils.escapeXml(endpointResolver.idpEntityId(config.getAppCode())))
                .append("</saml:Issuer>");
        xml.append("<saml:Subject><saml:NameID Format=\"").append(nameIdFormat).append("\">")
                .append(nameId).append("</saml:NameID>");
        xml.append("<saml:SubjectConfirmation Method=\"")
                .append(SamlProtocolConstants.BEARER_CONFIRMATION_METHOD).append("\">");
        xml.append("<saml:SubjectConfirmationData NotOnOrAfter=\"")
                .append(DateTimeFormatter.ISO_INSTANT.format(notOnOrAfter))
                .append("\" Recipient=\"").append(recipient)
                .append("\" InResponseTo=\"").append(SamlXmlUtils.escapeXml(authnRequest.getId()))
                .append("\"/></saml:SubjectConfirmation></saml:Subject>");
        xml.append("<saml:Conditions NotBefore=\"")
                .append(DateTimeFormatter.ISO_INSTANT.format(notBefore))
                .append("\" NotOnOrAfter=\"").append(DateTimeFormatter.ISO_INSTANT.format(notOnOrAfter))
                .append("\"><saml:AudienceRestriction><saml:Audience>").append(audience)
                .append("</saml:Audience></saml:AudienceRestriction></saml:Conditions>");
        xml.append("<saml:AuthnStatement AuthnInstant=\"").append(issueInstant)
                .append("\" SessionIndex=\"").append(sessionIndex)
                .append("\"><saml:AuthnContext><saml:AuthnContextClassRef>")
                .append(SamlProtocolConstants.AUTHN_CONTEXT_PASSWORD_PROTECTED_TRANSPORT)
                .append("</saml:AuthnContextClassRef></saml:AuthnContext></saml:AuthnStatement>");
        if (!attributes.isEmpty()) {
            xml.append("<saml:AttributeStatement>");
            attributes.forEach((name, value) -> xml.append("<saml:Attribute Name=\"")
                    .append(SamlXmlUtils.escapeXml(name)).append("\" NameFormat=\"")
                    .append(SamlProtocolConstants.ATTRIBUTE_NAME_FORMAT_BASIC)
                    .append("\"><saml:AttributeValue>")
                    .append(SamlXmlUtils.escapeXml(value))
                    .append("</saml:AttributeValue></saml:Attribute>"));
            xml.append("</saml:AttributeStatement>");
        }
        xml.append("</saml:Assertion></samlp:Response>");
        return xml.toString();
    }

    private String resolveNameId(SamlProtocolConfig config, UserDetails user) {
        SamlNameIdFormat format = config.getNameIdFormat();
        return switch (format) {
            case PERSISTENT, ENTITY -> user.getId();
            case EMAIL -> StringUtils.defaultIfBlank(user.getEmail(), user.getUsername());
            case TRANSIENT -> SamlXmlUtils.newId();
            case UNSPECIFIED -> user.getUsername();
        };
    }
}