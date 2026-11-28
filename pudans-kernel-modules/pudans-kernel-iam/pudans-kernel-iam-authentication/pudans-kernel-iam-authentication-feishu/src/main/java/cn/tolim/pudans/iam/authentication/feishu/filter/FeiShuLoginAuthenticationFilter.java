package cn.tolim.pudans.iam.authentication.feishu.filter;

import cn.tolim.pudans.iam.authentication.core.IdentityProviderAuthenticationService;
import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderUserDetails;
import cn.tolim.pudans.iam.authentication.core.client.RegisteredIdentityProviderClient;
import cn.tolim.pudans.iam.authentication.core.client.RegisteredIdentityProviderClientRepository;
import cn.tolim.pudans.iam.authentication.core.filter.AbstractIdentityProviderAuthenticationProcessingFilter;
import cn.tolim.pudans.iam.authentication.feishu.FeiShuIdentityProviderOAuth2Config;
import cn.tolim.pudans.iam.core.context.ContextService;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.infra.util.HttpClientUtils;
import cn.tolim.pudans.iam.infra.util.UrlUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nimbusds.oauth2.sdk.GrantType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.message.BasicHeader;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static cn.tolim.pudans.iam.authentication.core.IdentityProviderType.FEISHU_OAUTH;
import static cn.tolim.pudans.iam.authentication.core.constant.AuthenticationConstants.*;
import static cn.tolim.pudans.iam.authentication.feishu.constant.FeiShuAuthenticationConstants.*;


public class FeiShuLoginAuthenticationFilter extends
        AbstractIdentityProviderAuthenticationProcessingFilter {

    public final static String DEFAULT_FILTER_PROCESSES_URI = FEISHU_OAUTH
            .getLoginPathPrefix() + "/" + "{" + PROVIDER_CODE + "}";
    public static final AntPathRequestMatcher REQUEST_MATCHER = new AntPathRequestMatcher(
            DEFAULT_FILTER_PROCESSES_URI, HttpMethod.GET.name());

    // Creates a new instance
    public FeiShuLoginAuthenticationFilter(RegisteredIdentityProviderClientRepository registeredIdentityProviderClientRepository,
                                           IdentityProviderAuthenticationService identityProviderAuthenticationService) {
        super(REQUEST_MATCHER, identityProviderAuthenticationService,
                registeredIdentityProviderClientRepository);
    }

    public static String getLoginUrl(String providerId) {
        String url = ContextService.getPortalPublicBaseUrl() + FEISHU_OAUTH.getLoginPathPrefix()
                + "/" + providerId;
        return UrlUtils.format(url);
    }

    public static RequestMatcher getRequestMatcher() {
        return REQUEST_MATCHER;
    }

    // Feishu authentication
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response) throws AuthenticationException,
            IOException {
        OAuth2AuthorizationRequest authorizationRequest = getOauth2AuthorizationRequest(request,
                response);
        RequestMatcher.MatchResult matcher = REQUEST_MATCHER.matcher(request);
        Map<String, String> variables = matcher.getVariables();
        String providerCode = variables.get(PROVIDER_CODE);
        String providerId = getIdentityProviderId(providerCode);
        // code
        String code = request.getParameter(OAuth2ParameterNames.CODE);
        if (StringUtils.isEmpty(code)) {
            logger.error("飞书登录 code 参数不存在，认证失败");
            OAuth2Error oauth2Error = new OAuth2Error(INVALID_CODE_PARAMETER_ERROR_CODE);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        }
        // state
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (StringUtils.isEmpty(state)) {
            logger.error("飞书登录 state 参数不存在，认证失败");
            OAuth2Error oauth2Error = new OAuth2Error(INVALID_STATE_PARAMETER_ERROR_CODE);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        }
        if (!authorizationRequest.getState().equals(state)) {
            logger.error("飞书登录 state 匹配不一致，认证失败");
            OAuth2Error oauth2Error = new OAuth2Error(INVALID_STATE_PARAMETER_ERROR_CODE);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        }
        // Get identity provider
        RegisteredIdentityProviderClient<FeiShuIdentityProviderOAuth2Config> provider = getRegisteredIdentityProviderClient(
                providerCode);
        FeiShuIdentityProviderOAuth2Config config = provider.getConfig();
        if (Objects.isNull(config)) {
            logger.error("未查询到飞书扫码登录配置");
            // Invalid identity provider
            OAuth2Error oauth2Error = new OAuth2Error(
                    AbstractIdentityProviderAuthenticationProcessingFilter.INVALID_IDP_CONFIG);
            throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
        }
        // Get access token
        HashMap<String, String> param = new HashMap<>(16);
        param.put(CLIENT_ID, config.getAppId());
        param.put(CLIENT_SECRET, config.getAppSecret());
        param.put(OAuth2ParameterNames.CODE, code);
        param.put(OAuth2ParameterNames.REDIRECT_URI, getLoginUrl(provider.getCode()));
        param.put(OAuth2ParameterNames.GRANT_TYPE, GrantType.AUTHORIZATION_CODE.getValue());
        JSONObject result = JSON.parseObject(HttpClientUtils.post(ACCESS_TOKEN, param));
        // Get user information
        param = new HashMap<>(16);
        BasicHeader authorization = new BasicHeader(
                "Authorization", result.getString(OAuth2ParameterNames.TOKEN_TYPE) + " "
                + result.getString(OAuth2ParameterNames.ACCESS_TOKEN));
        result = JSON.parseObject(HttpClientUtils.get(USER_INFO, param, authorization));
        // Return
        IdentityProviderUserDetails identityProviderUserDetails = IdentityProviderUserDetails
                .builder().openId(result.getString(OPEN_ID)).unionId(result.getString("union_id"))
                .email(result.getString("email")).mobile(result.getString("mobile"))
                .nickName(result.getString("name")).avatarUrl(result.getString("avatar_url"))
                .providerType(FEISHU_OAUTH).providerCode(providerCode).providerId(providerId).build();
        return attemptAuthentication(request, response, identityProviderUserDetails);
    }
}
