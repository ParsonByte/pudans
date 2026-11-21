package cn.tolim.pudans.security.authentication.token;

import static cn.tolim.pudans.security.contract.PersonalAccessToken.PAT_TOKEN_PREFIX;

import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.core.rbac.UserRoles;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
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

    // 预先构建个人令牌与签名令牌两条认证链路。
    TokenAuthenticationManager(
            ReactiveExtensionClient client,
            ReactiveJwtDecoder jwtDecoder,
            Roles roleService,
            UserRoles userRoles,
            MasterKeys masterKeys) {
        this.client = client;
        this.jwtDecoder = jwtDecoder;
        this.patAuthManager = createPatAuthManager(roleService, userRoles, masterKeys);
        this.jwtAuthManager = createJwtAuthManager();
    }

    // 依据令牌前缀区分个人访问令牌与签名令牌。
    private static boolean isPatToken(Authentication a) {
        return a instanceof BearerTokenAuthenticationToken t && t.getToken().startsWith(PAT_TOKEN_PREFIX);
    }

    // 按令牌类型分派到对应的认证管理器。
    @Override
    public Mono<Authentication> authenticate(Authentication a) {
        if (isPatToken(a)) {
            return patAuthManager.authenticate(a);
        }
        return jwtAuthManager.authenticate(a);
    }

    // 以标准认证管理器为委托创建个人令牌管理器。
    private ReactiveAuthenticationManager createPatAuthManager(
            Roles roleService, UserRoles userRoles, MasterKeys masterKeys) {
        var delegate = new JwtReactiveAuthenticationManager(this.jwtDecoder);
        return new PatAuthenticationManager(client, delegate, roleService, userRoles, masterKeys);
    }

    // 构建签名令牌认证管理器并拒绝个人访问令牌。
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
