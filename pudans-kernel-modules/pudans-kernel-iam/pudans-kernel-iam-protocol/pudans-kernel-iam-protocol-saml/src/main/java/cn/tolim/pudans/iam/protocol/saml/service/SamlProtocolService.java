package cn.tolim.pudans.iam.protocol.saml.service;

import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlBindingType;
import cn.tolim.pudans.iam.protocol.saml.model.SamlAuthnRequest;
import cn.tolim.pudans.iam.protocol.saml.model.SamlProtocolConfig;


public interface SamlProtocolService {

    // Generate identity provider metadata for the application.
    String generateMetadata(SamlProtocolConfig config);

    // Parse an encoded AuthnRequest.
    SamlAuthnRequest parseAuthnRequest(String samlRequest, SamlBindingType bindingType);

    // Build and sign a SAML response for the authenticated user.
    String buildSignedResponse(SamlProtocolConfig config, SamlAuthnRequest authnRequest,
                               UserDetails user);
}