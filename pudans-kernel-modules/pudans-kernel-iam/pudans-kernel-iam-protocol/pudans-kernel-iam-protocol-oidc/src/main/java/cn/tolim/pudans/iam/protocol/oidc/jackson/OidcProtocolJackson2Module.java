package cn.tolim.pudans.iam.protocol.oidc.jackson;

import com.fasterxml.jackson.databind.Module;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;

import java.util.ArrayList;
import java.util.List;


public class OidcProtocolJackson2Module {
    public OidcProtocolJackson2Module() {
    }

    public static List<Module> getModules() {
        List<Module> modules = new ArrayList<>();
        modules.add(new OAuth2AuthorizationModule());
        modules.add(new OAuth2AuthorizationServerJackson2Module());
        return modules;
    }
}
