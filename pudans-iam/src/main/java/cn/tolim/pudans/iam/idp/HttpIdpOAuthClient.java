package cn.tolim.pudans.iam.idp;

import cn.tolim.pudans.iam.authentication.core.IdentityProviderType;
import cn.tolim.pudans.iam.authentication.dingtalk.DingTalkIdentityProviderOAuth2Config;
import cn.tolim.pudans.iam.authentication.feishu.FeiShuIdentityProviderOAuth2Config;
import cn.tolim.pudans.iam.communal.entity.authn.IdentityProviderEntity;
import cn.tolim.pudans.iam.core.auth.exception.InvalidAuthRequestException;
import cn.tolim.pudans.iam.infra.util.HttpClientUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.message.BasicHeader;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class HttpIdpOAuthClient implements IdpOAuthClient {

    private static final String DINGTALK_AUTHORIZE = "https://login.dingtalk.com/oauth2/auth";
    private static final String DINGTALK_TOKEN = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
    private static final String DINGTALK_USER = "https://api.dingtalk.com/v1.0/contact/users/me";

    private static final String FEISHU_AUTHORIZE =
            "https://passport.feishu.cn/suite/passport/oauth/authorize";
    private static final String FEISHU_TOKEN = "https://passport.feishu.cn/suite/passport/oauth/token";
    private static final String FEISHU_USER = "https://passport.feishu.cn/suite/passport/oauth/userinfo";

    @Override
    public String authorizeUrl(IdentityProviderEntity provider, String state, String redirectUri) {
        String type = provider.getType();
        if (IdentityProviderType.DINGTALK_OAUTH.value().equals(type)) {
            DingTalkIdentityProviderOAuth2Config config = parseDingTalk(provider);
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(DINGTALK_AUTHORIZE)
                    .queryParam("redirect_uri", redirectUri)
                    .queryParam("response_type", "code")
                    .queryParam("client_id", config.getAppKey())
                    .queryParam("state", state)
                    .queryParam("prompt", "consent");
            if (StringUtils.isNotBlank(config.getCorpId())) {
                builder.queryParam("scope", "openid corpid").queryParam("corpId", config.getCorpId());
            } else {
                builder.queryParam("scope", "openid");
            }
            return builder.encode(StandardCharsets.UTF_8).build().toUriString();
        }
        if (IdentityProviderType.FEISHU_OAUTH.value().equals(type)) {
            FeiShuIdentityProviderOAuth2Config config = parseFeishu(provider);
            return UriComponentsBuilder.fromUriString(FEISHU_AUTHORIZE)
                    .queryParam("client_id", config.getAppId())
                    .queryParam("redirect_uri", redirectUri)
                    .queryParam("response_type", "code")
                    .queryParam("state", state)
                    .encode(StandardCharsets.UTF_8).build().toUriString();
        }
        throw new InvalidAuthRequestException("idp_type_invalid",
                "Only DingTalk and Feishu QR login are supported");
    }

    @Override
    public IdpUserProfile exchange(IdentityProviderEntity provider, String code, String redirectUri) {
        String type = provider.getType();
        if (IdentityProviderType.DINGTALK_OAUTH.value().equals(type)) {
            return exchangeDingTalk(parseDingTalk(provider), code);
        }
        if (IdentityProviderType.FEISHU_OAUTH.value().equals(type)) {
            return exchangeFeishu(parseFeishu(provider), code, redirectUri);
        }
        throw new InvalidAuthRequestException("idp_type_invalid",
                "Only DingTalk and Feishu QR login are supported");
    }

    private IdpUserProfile exchangeDingTalk(DingTalkIdentityProviderOAuth2Config config, String code) {
        Map<String, Object> tokenRequest = new HashMap<>();
        tokenRequest.put("clientId", config.getAppKey());
        tokenRequest.put("clientSecret", config.getAppSecret());
        tokenRequest.put("code", code);
        tokenRequest.put("grantType", "authorization_code");
        JSONObject token = JSON.parseObject(HttpClientUtils.doPostJson(DINGTALK_TOKEN,
                JSON.toJSONString(tokenRequest)));
        String accessToken = token.getString("accessToken");
        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalStateException("DingTalk access token is missing");
        }
        JSONObject user = JSON.parseObject(get(DINGTALK_USER,
                new BasicHeader("x-acs-dingtalk-access-token", accessToken)));
        return new IdpUserProfile(user.getString("openId"), user.getString("unionId"),
                user.getString("email"), user.getString("mobile"), user.getString("nick"),
                user.getString("avatarUrl"));
    }

    private IdpUserProfile exchangeFeishu(FeiShuIdentityProviderOAuth2Config config, String code,
                                           String redirectUri) {
        Map<String, String> tokenRequest = new HashMap<>();
        tokenRequest.put("client_id", config.getAppId());
        tokenRequest.put("client_secret", config.getAppSecret());
        tokenRequest.put("code", code);
        tokenRequest.put("redirect_uri", redirectUri);
        tokenRequest.put("grant_type", "authorization_code");
        JSONObject token = JSON.parseObject(HttpClientUtils.doPost(FEISHU_TOKEN, tokenRequest));
        String accessToken = token.getString("access_token");
        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalStateException("Feishu access token is missing");
        }
        JSONObject user = JSON.parseObject(get(FEISHU_USER,
                new BasicHeader("Authorization", "Bearer " + accessToken)));
        return new IdpUserProfile(user.getString("open_id"), user.getString("union_id"),
                user.getString("email"), user.getString("mobile"), user.getString("name"),
                user.getString("avatar_url"));
    }

    private String get(String url, BasicHeader header) {
        try {
            return HttpClientUtils.get(url, Map.of(), header);
        } catch (IOException exception) {
            throw new IllegalStateException("Identity provider request failed", exception);
        }
    }

    private DingTalkIdentityProviderOAuth2Config parseDingTalk(IdentityProviderEntity provider) {
        DingTalkIdentityProviderOAuth2Config config = JSON.parseObject(provider.getConfig(),
                DingTalkIdentityProviderOAuth2Config.class);
        if (config == null || StringUtils.isBlank(config.getAppKey())
                || StringUtils.isBlank(config.getAppSecret())) {
            throw new InvalidAuthRequestException("idp_config_invalid",
                    "DingTalk identity provider configuration is incomplete");
        }
        return config;
    }

    private FeiShuIdentityProviderOAuth2Config parseFeishu(IdentityProviderEntity provider) {
        FeiShuIdentityProviderOAuth2Config config = JSON.parseObject(provider.getConfig(),
                FeiShuIdentityProviderOAuth2Config.class);
        if (config == null || StringUtils.isBlank(config.getAppId())
                || StringUtils.isBlank(config.getAppSecret())) {
            throw new InvalidAuthRequestException("idp_config_invalid",
                    "Feishu identity provider configuration is incomplete");
        }
        return config;
    }
}