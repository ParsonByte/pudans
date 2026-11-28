package cn.tolim.pudans.iam.protocol.saml.signature;

import cn.tolim.pudans.iam.protocol.saml.exception.SamlErrorCodes;
import cn.tolim.pudans.iam.protocol.saml.exception.SamlProtocolException;
import cn.tolim.pudans.iam.protocol.saml.model.SamlProtocolConfig;
import org.springframework.http.HttpStatus;


public class UnsupportedSamlResponseSigner implements SamlResponseSigner {

    @Override
    public String sign(String samlResponseXml, SamlProtocolConfig config) {
        // Signing is disabled for this protocol configuration.
        throw new SamlProtocolException(SamlErrorCodes.SIGNING_UNAVAILABLE,
                "SAML response signing is not enabled for this protocol configuration.",
                HttpStatus.NOT_IMPLEMENTED);
    }
}