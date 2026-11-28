package cn.tolim.pudans.iam.protocol.saml.service;

import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlBindingType;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlNameIdFormat;
import cn.tolim.pudans.iam.protocol.saml.exception.SamlErrorCodes;
import cn.tolim.pudans.iam.protocol.saml.exception.SamlProtocolException;
import cn.tolim.pudans.iam.protocol.saml.model.SamlAuthnRequest;
import cn.tolim.pudans.iam.protocol.saml.model.SamlProtocolConfig;
import cn.tolim.pudans.iam.protocol.saml.util.SamlXmlUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


class DefaultSamlProtocolServiceTests {

    private static final String PROTOCOL_NS = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String ASSERTION_NS = "urn:oasis:names:tc:SAML:2.0:assertion";

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

    private final DefaultSamlProtocolService service = new DefaultSamlProtocolService(
            (xml, config) -> xml, resolver);

    private static UserDetails user() {
        UserDetails user = new UserDetails("user-1", "alice", UserType.USER, true, true, true,
                true, List.of());
        user.setEmail("alice@example.test");
        user.setFullName("Alice");
        return user;
    }

    private static String authnRequestXml() {
        return "<samlp:AuthnRequest xmlns:samlp=\"" + PROTOCOL_NS + "\" xmlns:saml=\""
                + ASSERTION_NS + "\" ID=\"_request-1\" Version=\"2.0\""
                + " IssueInstant=\"2026-01-01T00:00:00Z\""
                + " Destination=\"https://idp.test/sso\""
                + " AssertionConsumerServiceURL=\"https://sp.test/acs\""
                + " ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\">"
                + "<saml:Issuer>https://sp.test</saml:Issuer>"
                + "<samlp:NameIDPolicy Format=\"urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress\"/>"
                + "</samlp:AuthnRequest>";
    }

    @Test
    void generatesMetadataWithEntityIdEndpointsAndCertificate() {
        SamlProtocolConfig config = SamlProtocolConfig.builder()
                .appCode("app-1")
                .signingCertificate("-----BEGIN CERTIFICATE-----\nQUJD\n-----END CERTIFICATE-----")
                .nameIdFormat(SamlNameIdFormat.PERSISTENT)
                .wantAuthnRequestsSigned(true)
                .build();

        String metadata = service.generateMetadata(config);

        assertThat(metadata).contains("entityID=\"https://idp.test/v1/authorize/saml/app-1\"");
        assertThat(metadata).contains("Location=\"https://idp.test/v1/authorize/saml/app-1/sso\"");
        assertThat(metadata).contains("Location=\"https://idp.test/v1/authorize/saml/app-1/slo\"");
        assertThat(metadata).contains("<ds:X509Certificate>QUJD</ds:X509Certificate>");
        assertThat(metadata).contains("WantAuthnRequestsSigned=\"true\"");
        assertThat(metadata).contains(SamlBindingType.REDIRECT.getCode());
        assertThat(metadata).contains(SamlBindingType.POST.getCode());
    }

    @Test
    void parsesRedirectBindingAuthnRequest() {
        String encoded = SamlXmlUtils.encodeRedirectBinding(authnRequestXml());

        SamlAuthnRequest request = service.parseAuthnRequest(encoded, SamlBindingType.REDIRECT);

        assertThat(request.getId()).isEqualTo("_request-1");
        assertThat(request.getIssuer()).isEqualTo("https://sp.test");
        assertThat(request.getAssertionConsumerServiceUrl()).isEqualTo("https://sp.test/acs");
        assertThat(request.getDestination()).isEqualTo("https://idp.test/sso");
        assertThat(request.getNameIdFormat())
                .isEqualTo("urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress");
    }

    @Test
    void parsesPostBindingAuthnRequest() {
        String encoded = java.util.Base64.getEncoder()
                .encodeToString(authnRequestXml().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        SamlAuthnRequest request = service.parseAuthnRequest(encoded, SamlBindingType.POST);

        assertThat(request.getId()).isEqualTo("_request-1");
        assertThat(request.getIssuer()).isEqualTo("https://sp.test");
    }

    @Test
    void rejectsNonAuthnRequestXml() {
        String encoded = java.util.Base64.getEncoder()
                .encodeToString("<root/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        SamlProtocolException exception = assertThrows(SamlProtocolException.class,
                () -> service.parseAuthnRequest(encoded, SamlBindingType.POST));

        assertThat(exception.getErrorCode()).isEqualTo(SamlErrorCodes.INVALID_REQUEST);
    }

    @Test
    void buildsResponseWithSubjectRecipientAndAttributes() {
        SamlProtocolConfig config = SamlProtocolConfig.builder()
                .appCode("app-1")
                .entityId("https://sp.test")
                .assertionConsumerServiceUrl("https://sp.test/acs")
                .nameIdFormat(SamlNameIdFormat.EMAIL)
                .assertionTimeToLive(Duration.ofSeconds(120))
                .build();
        SamlAuthnRequest request = SamlAuthnRequest.builder()
                .id("_request-1")
                .issuer("https://sp.test")
                .build();

        String response = service.buildSignedResponse(config, request, user());

        assertThat(response).contains("<samlp:Response");
        assertThat(response).contains("InResponseTo=\"_request-1\"");
        assertThat(response).contains("Recipient=\"https://sp.test/acs\"");
        assertThat(response).contains("Format=\"urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress\"");
        assertThat(response).contains("alice@example.test");
        assertThat(response).contains("<saml:Audience>https://sp.test</saml:Audience>");
        assertThat(response).contains("Name=\"email\"");
        assertThat(response).contains("alice@example.test</saml:AttributeValue>");
    }

    @Test
    void rejectsResponseWithoutAssertionConsumerUrl() {
        SamlProtocolConfig config = SamlProtocolConfig.builder()
                .appCode("app-1")
                .entityId("https://sp.test")
                .build();
        SamlAuthnRequest request = SamlAuthnRequest.builder()
                .id("_request-1")
                .issuer("https://sp.test")
                .build();

        SamlProtocolException exception = assertThrows(SamlProtocolException.class,
                () -> service.buildSignedResponse(config, request, user()));

        assertThat(exception.getErrorCode()).isEqualTo(SamlErrorCodes.CONFIG_ERROR);
    }
}