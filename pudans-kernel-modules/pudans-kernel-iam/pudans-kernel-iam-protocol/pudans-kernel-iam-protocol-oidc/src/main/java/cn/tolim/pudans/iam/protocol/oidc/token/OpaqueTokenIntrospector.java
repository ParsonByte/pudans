package cn.tolim.pudans.iam.protocol.oidc.token;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.*;

import static org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN;


public class OpaqueTokenIntrospector implements
        org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector {

    private static final String AUTHORITY_PREFIX = "SCOPE_";

    private final Logger logger = LoggerFactory
            .getLogger(OpaqueTokenIntrospector.class);
    private final OAuth2AuthorizationService authorizationService;

    public OpaqueTokenIntrospector(OAuth2AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    // introspect
    // Parameter token the token to introspect
    // Returns OAuth2AuthenticatedPrincipal
    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        OAuth2Authorization authorization = authorizationService.findByToken(token, ACCESS_TOKEN);
        if (authorization == null) {
            this.logger.trace("令牌未激活，未执行校验");
            throw new BadOpaqueTokenException("提供的令牌未激活");
        }
        OAuth2Authorization.Token<OAuth2Token> authorizedToken = authorization.getToken(token);
        if (!Objects.isNull(authorizedToken) && !authorizedToken.isActive()) {
            this.logger.trace("令牌未激活，未执行校验");
            throw new BadOpaqueTokenException("提供的令牌未激活");
        }
        if (!Objects.isNull(authorizedToken)
                && !CollectionUtils.isEmpty(authorizedToken.getClaims())) {
            Map<String, Object> readValue = JSON.parseObject(JSON.toJSONString(authorizedToken));
            return convertClaimsSet(readValue);
        }
        return null;
    }

    // Copy the convertClaimsSet method from SpringOpaqueTokenIntrospector
    // Parameter claims Map
    // Returns OAuth2AuthenticatedPrincipal
    private OAuth2AuthenticatedPrincipal convertClaimsSet(Map<String, Object> claims) {
        claims.computeIfPresent(OAuth2TokenIntrospectionClaimNames.AUD, (k, v) -> {
            if (v instanceof String) {
                return Collections.singletonList(v);
            }
            return v;
        });
        claims.computeIfPresent(OAuth2TokenIntrospectionClaimNames.CLIENT_ID,
                (k, v) -> v.toString());
        claims.computeIfPresent(OAuth2TokenIntrospectionClaimNames.EXP,
                (k, v) -> Instant.ofEpochSecond(((Number) v).longValue()));
        claims.computeIfPresent(OAuth2TokenIntrospectionClaimNames.IAT,
                (k, v) -> Instant.ofEpochSecond(((Number) v).longValue()));
        // RFC-7662 page 7 directs users to RFC-7519 for defining the values of these
        // issuer fields.
        // https://datatracker.ietf.org/doc/html/rfc7662#page-7
        // RFC-7519 page 9 defines issuer fields as being 'case-sensitive' strings
        // containing
        // a 'StringOrURI', which is defined on page 5 as being any string, but strings
        // containing ':'
        // should be treated as valid URIs.
        // https://datatracker.ietf.org/doc/html/rfc7519#section-2
        // It is not defined however as to whether-or-not normalized URIs should be
        // treated as the same literal
        // value. It only defines validation itself, so to avoid potential ambiguity or
        // unwanted side effects that
        // may be awkward to debug, we do not want to manipulate this value. Previous
        // versions of Spring Security
        // would *only* allow valid URLs, which is not what we wish to achieve here.
        claims.computeIfPresent(OAuth2TokenIntrospectionClaimNames.ISS, (k, v) -> v.toString());
        claims.computeIfPresent(OAuth2TokenIntrospectionClaimNames.NBF,
                (k, v) -> Instant.ofEpochSecond(((Number) v).longValue()));
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        claims.computeIfPresent(OAuth2TokenIntrospectionClaimNames.SCOPE, (k, v) -> {
            if (v instanceof String) {
                Collection<String> scopes = Arrays.asList(((String) v).split(" "));
                for (String scope : scopes) {
                    authorities.add(new SimpleGrantedAuthority(AUTHORITY_PREFIX + scope));
                }
                return scopes;
            }
            return v;
        });
        return new OAuth2IntrospectionAuthenticatedPrincipal(claims, authorities);
    }
}
