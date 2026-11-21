package cn.tolim.pudans.security.authentication.token;

import static cn.tolim.pudans.contract.security.PersonalAccessToken.PAT_TOKEN_PREFIX;

import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.infra.setting.MasterKeys;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import reactor.core.publisher.Mono;


class TokenAuthenticationManager implements ReactiveAuthenticationManager {

    private final ReactiveExtensionClient client;

    private final ReactiveJwtDecoder jwtDecoder;

    private final ReactiveAuthenticationManager patAuthManager;

    private final ReactiveAuthenticationManager jwtAuthManager;

    TokenAuthenticationManager(
            ReactiveExtensionClient client,
            ReactiveJwtDecoder jwtDecoder,
            Roles roleService,
            Groups groups,
            MasterKeys masterKeys) {
        this.client = client;
        this.jwtDecoder = jwtDecoder;
        this.patAuthManager = createPatAuthManager(roleService, groups, masterKeys);
        this.jwtAuthManager = createJwtAuthManager();
    }

    private static boolean isPatToken(Authentication a) {
        return a instanceof BearerTokenAuthenticationToken t && t.getToken().startsWith(PAT_TOKEN_PREFIX);
    }

    @Override
    public Mono<Authentication> authenticate(Authentication a) {
        if (isPatToken(a)) {
            return patAuthManager.authenticate(a);
        }
        return jwtAuthManager.authenticate(a);
    }

    private ReactiveAuthenticationManager createPatAuthManager(
            Roles roleService, Groups groups, MasterKeys masterKeys) {
        var delegate = new JwtReactiveAuthenticationManager(this.jwtDecoder);
        return new PatAuthenticationManager(client, delegate, roleService, groups, masterKeys);
    }

    private ReactiveAuthenticationManager createJwtAuthManager() {
        var authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix(AuthorityUtils.ROLE_PREFIX);

        var converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new ReactiveJwtGrantedAuthoritiesConverterAdapter(authoritiesConverter));

        var authManager = new JwtReactiveAuthenticationManager(this.jwtDecoder);
        authManager.setJwtAuthenticationConverter(new NonPatJwtAuthenticationConverter(converter));
        return authManager;
    }
}
