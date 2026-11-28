package cn.tolim.pudans.iam.protocol.saml.signature;

import cn.tolim.pudans.iam.protocol.saml.model.SamlProtocolConfig;


public interface SamlResponseSigner {

    // Sign the given SAML response XML and return the signed document.
    String sign(String samlResponseXml, SamlProtocolConfig config);
}