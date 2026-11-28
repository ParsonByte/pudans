package cn.tolim.pudans.iam.protocol.saml.signature;

import cn.tolim.pudans.iam.protocol.saml.model.SamlProtocolConfig;


public interface SamlResponseVerifier {

    // Verify the XML signature on a SAML response or assertion and throw when it is invalid.
    void verify(String samlResponseXml, SamlProtocolConfig config);
}