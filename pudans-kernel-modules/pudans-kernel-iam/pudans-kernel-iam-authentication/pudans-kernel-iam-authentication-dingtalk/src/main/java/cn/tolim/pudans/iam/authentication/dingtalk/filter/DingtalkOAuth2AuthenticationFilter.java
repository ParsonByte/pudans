package cn.tolim.pudans.iam.authentication.dingtalk.filter;

import cn.tolim.pudans.iam.authentication.core.IdentityProviderAuthenticationService;
import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderUserDetails;
import cn.tolim.pudans.iam.authentication.core.client.RegisteredIdentityProviderClient;
import cn.tolim.pudans.iam.authentication.core.client.RegisteredIdentityProviderClientRepository;
import cn.tolim.pudans.iam.authentication.core.filter.AbstractIdentityProviderAuthenticationProcessingFilter;
import cn.tolim.pudans.iam.authentication.dingtalk.DingTalkIdentityProviderOAuth2Config;
import cn.tolim.pudans.iam.core.context.ContextService;
import cn.tolim.pudans.iam.infra.exception.PudansException;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.infra.trace.TraceUtils;
import cn.tolim.pudans.iam.infra.util.UrlUtils;
import com.aliyun.dingtalkcontact_1_0.models.GetUserHeaders;
import com.aliyun.dingtalkcontact_1_0.models.GetUserResponse;
import com.aliyun.dingtalkcontact_1_0.models.GetUserResponseBody;
import com.aliyun.dingtalkoauth2_1_0.Client;
import com.aliyun.dingtalkoauth2_1_0.models.GetUserTokenRequest;
import com.aliyun.dingtalkoauth2_1_0.models.GetUserTokenResponse;
import com.aliyun.dingtalkoauth2_1_0.models.GetUserTokenResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static cn.tolim.pudans.iam.authentication.core.IdentityProviderType.DINGTALK_OAUTH;
import static cn.tolim.pudans.iam.authentication.core.constant.AuthenticationConstants.*;
import static cn.tolim.pudans.iam.authentication.dingtalk.constant.DingTalkAuthenticationConstants.AUTH_CODE;


@SuppressWarnings("DuplicatedCode")
public class DingtalkOAuth2AuthenticationFilter extends
        AbstractIdentityProviderAuthenticationProcessingFilter {
    public final static String DEFAULT_FILTER_PROCESSES_URI = DINGTALK_OAUTH
            .getLoginPathPrefix() + "/" + "{" + PROVIDER_CODE + "}";
    // AntPathRequestMatcher
    public static final AntPathRequestMatcher REQUEST_MATCHER = new AntPathRequestMatcher(
            DEFAULT_FILTER_PROCESSES_URI, HttpMethod.GET.name());
    // Cache
    private Cache<String, String> cache;

    // Creates a new instance
    public DingtalkOAuth2AuthenticationFilter(RegisteredIdentityProviderClientRepository registeredIdentityProviderClientRepository,
                                              IdentityProviderAuthenticationService identityProviderAuthenticationService) {
        super(REQUEST_MATCHER, identityProviderAuthenticationService,
                registeredIdentityProviderClientRepository);
    }

    public static String getLoginUrl(String providerId) {
        String url = ContextService.getPortalPublicBaseUrl() + DINGTALK_OAUTH.getLoginPathPrefix()
                + "/" + providerId;
        return UrlUtils.format(url);
    }

    public static RequestMatcher getRequestMatcher() {
        return REQUEST_MATCHER;
    }

    // DingTalk authentication
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response) throws AuthenticationException,
            IOException {
        OAuth2AuthorizationRequest authorizationRequest = getOauth2AuthorizationRequest(request,
                response);
        TraceUtils.put(UUID.randomUUID().toString());
        RequestMatcher.MatchResult matcher = REQUEST_MATCHER.matcher(request);
        Map<String, String> variables = matcher.getVariables();
        String providerCode = variables.get(PROVIDER_CODE);
        String providerId = getIdentityProviderId(providerCode);
        // DingTalk uses authCode for the new login flow
        String code = request.getParameter(AUTH_CODE);
        if (StringUtils.isEmpty(code)) {
            logger.error("钉钉登录 code 参数不存在，认证失败");
            OAuth2Error oauth2Error = new OAuth2Error(INVALID_CODE_PARAMETER_ERROR_CODE);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        }
        // state
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (StringUtils.isEmpty(state)) {
            logger.error("钉钉登录 state 参数不存在，认证失败");
            OAuth2Error oauth2Error = new OAuth2Error(INVALID_STATE_PARAMETER_ERROR_CODE);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        }
        // Validate state
        if (!authorizationRequest.getState().equals(state)) {
            logger.error("钉钉登录 state 匹配不一致，认证失败");
            OAuth2Error oauth2Error = new OAuth2Error(INVALID_STATE_PARAMETER_ERROR_CODE);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        }
        // Get identity provider
        RegisteredIdentityProviderClient<DingTalkIdentityProviderOAuth2Config> provider = getRegisteredIdentityProviderClient(
                providerCode);
        DingTalkIdentityProviderOAuth2Config idpOauthConfig = provider.getConfig();
        if (Objects.isNull(idpOauthConfig)) {
            logger.error("未查询到钉钉登录配置");
            // Invalid identity provider
            OAuth2Error oauth2Error = new OAuth2Error(INVALID_IDP_CONFIG);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        }
        String accessToken = getToken(code, idpOauthConfig);
        Config config = new Config();
        config.setProtocol("https");
        config.setRegionId("central");
        GetUserHeaders getUserHeaders = new GetUserHeaders();
        getUserHeaders.setXAcsDingtalkAccessToken(accessToken);
        // Get user profile. To get the current authorizer's information, pass me as unionId.
        GetUserResponse user;
        try {
            com.aliyun.dingtalkcontact_1_0.Client client = new com.aliyun.dingtalkcontact_1_0.Client(
                    config);
            user = client.getUserWithOptions("me", getUserHeaders, new RuntimeOptions());
        } catch (Exception e) {
            logger.error("钉钉认证获取用户信息失败: {}", e);
            throw new PudansException("钉钉认证获取用户信息失败", e);
        }
        // Execution logic
        GetUserResponseBody body = user.getBody();
        IdentityProviderUserDetails identityProviderUserDetails = IdentityProviderUserDetails
                .builder().openId(body.getOpenId()).unionId(body.getUnionId()).email(body.getEmail())
                .stateCode(body.getStateCode()).mobile(body.getMobile()).nickName(body.getNick())
                .avatarUrl(body.getAvatarUrl()).providerType(DINGTALK_OAUTH).providerCode(providerCode)
                .providerId(providerId).build();
        return attemptAuthentication(request, response, identityProviderUserDetails);
    }

    // Get token
    public String getToken(String authCode, DingTalkIdentityProviderOAuth2Config config) {
        String cacheKey = OAuth2ParameterNames.ACCESS_TOKEN + DigestUtils.md5Hex(config.toString());
        if (!Objects.isNull(cache)) {
            return cache.getIfPresent(cacheKey);
        }
        Config clientConfig = new Config();
        clientConfig.setProtocol("https");
        clientConfig.setRegionId("central");
        try {
            Client client = new Client(clientConfig);
            GetUserTokenRequest getUserTokenRequest = new GetUserTokenRequest()
                    // Application basic information: AppKey
                    .setClientId(config.getAppKey())
                    // Application basic information: AppSecret
                    .setClientSecret(config.getAppSecret()).setCode(authCode)
                    .setGrantType(AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
            // Get user personal token
            GetUserTokenResponse getUserTokenResponse = client.getUserToken(getUserTokenRequest);
            GetUserTokenResponseBody body = getUserTokenResponse.getBody();
            // Put in cache
            cache = Caffeine.newBuilder().expireAfterWrite(body.getExpireIn(), TimeUnit.SECONDS)
                    .build();
            cache.put(cacheKey, body.getAccessToken());
            return cache.getIfPresent(cacheKey);
        } catch (Exception exception) {
            throw new RuntimeException(exception.getMessage());
        }
    }
}
