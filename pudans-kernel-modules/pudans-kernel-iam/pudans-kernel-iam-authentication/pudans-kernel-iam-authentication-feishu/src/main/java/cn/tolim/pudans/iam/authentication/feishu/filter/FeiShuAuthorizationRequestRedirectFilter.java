package cn.tolim.pudans.iam.authentication.feishu.filter;

import cn.tolim.pudans.iam.authentication.core.client.RegisteredIdentityProviderClient;
import cn.tolim.pudans.iam.authentication.core.client.RegisteredIdentityProviderClientRepository;
import cn.tolim.pudans.iam.authentication.feishu.FeiShuIdentityProviderOAuth2Config;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import com.google.common.collect.Maps;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

import static cn.tolim.pudans.iam.authentication.core.IdentityProviderType.FEISHU_OAUTH;
import static cn.tolim.pudans.iam.authentication.core.constant.AuthenticationConstants.PROVIDER_CODE;
import static cn.tolim.pudans.iam.authentication.feishu.constant.FeiShuAuthenticationConstants.*;
import static cn.tolim.pudans.iam.authentication.feishu.filter.FeiShuLoginAuthenticationFilter.getLoginUrl;
import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.RESPONSE_TYPE;


public class FeiShuAuthorizationRequestRedirectFilter extends OncePerRequestFilter {

    // AntPathRequestMatcher
    public static final AntPathRequestMatcher FEI_SHU_SCAN_CODE_REQUEST_MATCHER = new AntPathRequestMatcher(
            FEISHU_OAUTH.getAuthorizationPathPrefix() + "/" + "{" + PROVIDER_CODE + "}",
            HttpMethod.GET.name());
    private static final StringKeyGenerator DEFAULT_STATE_GENERATOR = new Base64StringKeyGenerator(
            Base64.getUrlEncoder());
    private final Logger logger = LoggerFactory
            .getLogger(FeiShuAuthorizationRequestRedirectFilter.class);
    // Authorization request repository
    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository = new HttpSessionOAuth2AuthorizationRequestRepository();
    private final RegisteredIdentityProviderClientRepository registeredIdentityProviderClientRepository;
    // Redirect policy
    private final RedirectStrategy authorizationRedirectStrategy = new DefaultRedirectStrategy();

    public FeiShuAuthorizationRequestRedirectFilter(RegisteredIdentityProviderClientRepository registeredIdentityProviderClientRepository) {
        this.registeredIdentityProviderClientRepository = registeredIdentityProviderClientRepository;
    }

    public static RequestMatcher getRequestMatcher() {
        return FEI_SHU_SCAN_CODE_REQUEST_MATCHER;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws IOException,
            ServletException {
        RequestMatcher.MatchResult matcher = FEI_SHU_SCAN_CODE_REQUEST_MATCHER.matcher(request);
        if (!matcher.isMatch()) {
            filterChain.doFilter(request, response);
            return;
        }
        Map<String, String> variables = matcher.getVariables();
        String providerCode = variables.get(PROVIDER_CODE);
        Optional<RegisteredIdentityProviderClient<FeiShuIdentityProviderOAuth2Config>> optional = registeredIdentityProviderClientRepository
                .findByCode(providerCode);
        if (optional.isEmpty()) {
            throw new NullPointerException("未查询到身份提供商信息");
        }
        RegisteredIdentityProviderClient<FeiShuIdentityProviderOAuth2Config> entity = optional
                .get();
        FeiShuIdentityProviderOAuth2Config config = entity.getConfig();
        Assert.notNull(config, "飞书扫码登录配置不能为空");
        // Build authorization request
        HashMap<String, Object> attributes = Maps.newHashMap();
        attributes.put(RESPONSE_TYPE, CODE);
        OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.authorizationCode()
                .clientId(config.getAppId())
                .authorizationUri(AUTHORIZATION_REQUEST)
                .redirectUri(getLoginUrl(optional.get().getCode()))
                .state(DEFAULT_STATE_GENERATOR.generateKey())
                .attributes(attributes);
        builder.parameters(parameters -> {
            HashMap<String, Object> linkedParameters = new LinkedHashMap<>();
            parameters.forEach((key, value) -> {
                if (OAuth2ParameterNames.CLIENT_ID.equals(key)) {
                    linkedParameters.put(CLIENT_ID, value);
                }
                if (OAuth2ParameterNames.STATE.equals(key)) {
                    linkedParameters.put(OAuth2ParameterNames.STATE, value);
                }
                if (OAuth2ParameterNames.REDIRECT_URI.equals(key)) {
                    linkedParameters.put(OAuth2ParameterNames.REDIRECT_URI, value);
                }
                if (RESPONSE_TYPE.equals(key)) {
                    linkedParameters.put(RESPONSE_TYPE, value);
                }
            });
            parameters.clear();
            parameters.putAll(linkedParameters);
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
