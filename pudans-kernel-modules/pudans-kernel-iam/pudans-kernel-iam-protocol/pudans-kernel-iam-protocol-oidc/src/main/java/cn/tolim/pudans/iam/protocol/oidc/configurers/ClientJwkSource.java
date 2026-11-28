package cn.tolim.pudans.iam.protocol.oidc.configurers;

import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContext;
import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContextHolder;
import cn.tolim.pudans.iam.appkernel.oidc.model.OidcProtocolConfig;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import java.util.List;
import java.util.Map;


public class ClientJwkSource implements JWKSource<SecurityContext> {

    @Override
    public List<JWK> get(JWKSelector jwkSelector,
                         SecurityContext context) throws KeySourceException {
        // Get application certificate by application code
        ApplicationContext applicationContext = ApplicationContextHolder.getApplicationContext();
        Map<String, Object> config = applicationContext.getConfig();
        OidcProtocolConfig protocolConfig = (OidcProtocolConfig) config
                .get(OidcProtocolConfig.class.getName());
        return jwkSelector.select(new JWKSet(protocolConfig.getJwks()));
    }
}
