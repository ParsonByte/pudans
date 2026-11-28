package cn.tolim.pudans.iam.authentication.dingtalk.filter;

import cn.tolim.pudans.iam.authentication.core.client.RegisteredIdentityProviderClient;
import cn.tolim.pudans.iam.authentication.core.client.RegisteredIdentityProviderClientRepository;
import cn.tolim.pudans.iam.authentication.dingtalk.DingTalkIdentityProviderOAuth2Config;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static cn.tolim.pudans.iam.authentication.core.IdentityProviderType.DINGTALK_OAUTH;
import static cn.tolim.pudans.iam.authentication.core.constant.AuthenticationConstants.PROVIDER_CODE;
import static cn.tolim.pudans.iam.authentication.dingtalk.constant.DingTalkAuthenticationConstants.CORP_ID;
import static cn.tolim.pudans.iam.authentication.dingtalk.constant.DingTalkAuthenticationConstants.URL_AUTHORIZE;
import static cn.tolim.pudans.iam.authentication.dingtalk.filter.DingtalkOAuth2AuthenticationFilter.getLoginUrl;
import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.RESPONSE_TYPE;


@SuppressWarnings("ALL")
public class DingtalkOAuth2AuthorizationRequestRedirectFilter extends OncePerRequestFilter {

    // AntPathRequestMatcher
    public static final AntPathRequestMatcher DINGTALK_OAUTH2_REQUEST_MATCHER = new AntPathRequestMatcher(
            DINGTALK_OAUTH.getAuthorizationPathPrefix() + "/" + "{" + PROVIDER_CODE + "}",
            HttpMethod.GET.name());
    private static final StringKeyGenerator DEFAULT_STATE_GENERATOR = new Base64StringKeyGenerator(
            Base64.getUrlEncoder());
    private final Logger logger = LoggerFactory
            .getLogger(DingtalkOAuth2AuthorizationRequestRedirectFilter.class);
    // Redirect policy
    private final RedirectStrategy authorizationRedirectStrategy = new DefaultRedirectStrategy();
    // Authorization request repository
    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository = new HttpSessionOAuth2AuthorizationRequestRepository();
    private final RegisteredIdentityProviderClientRepository registeredIdentityProviderClientRepository;

    public DingtalkOAuth2AuthorizationRequestRedirectFilter(RegisteredIdentityProviderClientRepository registeredIdentityProviderClientRepository) {
        this.registeredIdentityProviderClientRepository = registeredIdentityProviderClientRepository;
    }

    public static RequestMatcher getRequestMatcher() {
        return DINGTALK_OAUTH2_REQUEST_MATCHER;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws IOException,
            ServletException {
        RequestMatcher.MatchResult matcher = DINGTALK_OAUTH2_REQUEST_MATCHER.matcher(request);
        if (!matcher.isMatch()) {
            filterChain.doFilter(request, response);
            return;
        }
        Map<String, String> variables = matcher.getVariables();
        String providerCode = variables.get(PROVIDER_CODE);
        Optional<RegisteredIdentityProviderClient<DingTalkIdentityProviderOAuth2Config>> optional = registeredIdentityProviderClientRepository
                .findByCode(providerCode);
        if (optional.isEmpty()) {
            throw new NullPointerException("未查询到身份提供商信息");
        }
        RegisteredIdentityProviderClient<DingTalkIdentityProviderOAuth2Config> entity = optional
                .get();
        DingTalkIdentityProviderOAuth2Config config = entity.getConfig();
        Assert.notNull(config, "钉钉登录配置不能为空");
        // Build authorization request
        OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.authorizationCode()
                .clientId(config.getAppKey()).authorizationUri(URL_AUTHORIZE)
                .redirectUri(getLoginUrl(optional.get().getCode()))
                .state(DEFAULT_STATE_GENERATOR.generateKey());
        // Add CorpId
        if (StringUtils.isNotBlank(config.getCorpId())) {
            builder.scope(OidcScopes.OPENID + " corpid");
        } else {
            builder.scope(OidcScopes.OPENID);
        }
        builder.parameters(parameters -> {
            parameters.put(RESPONSE_TYPE, OAuth2ParameterNames.CODE);
            parameters.put("prompt", "consent");
            // Add CorpId
            if (StringUtils.isNotBlank(config.getCorpId())) {
                parameters.put(CORP_ID, config.getCorpId());
            }
        });
        this.sendRedirectForAuthorization(request, response, builder.build());
    }

    private void sendRedirectForAuthorization(HttpServletRequest request,
                                              HttpServletResponse response,
                                              OAuth2AuthorizationRequest authorizationRequest) throws IOException {
        this.authorizationRequestRepository.saveAuthorizationRequest(authorizationRequest, request,
                response);
        this.authorizationRedirectStrategy.sendRedirect(request, response,
                authorizationRequest.getAuthorizationRequestUri());
    }
}
