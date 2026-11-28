package cn.tolim.pudans.iam.idp;

import cn.tolim.pudans.contract.iam.dto.auth.IdpQrPollRequest;
import cn.tolim.pudans.contract.iam.dto.auth.IdpQrPollResponse;
import cn.tolim.pudans.contract.iam.dto.auth.IdpQrStartRequest;
import cn.tolim.pudans.contract.iam.dto.auth.IdpQrStartResponse;
import cn.tolim.pudans.contract.iam.dto.auth.LoginResponse;
import cn.tolim.pudans.iam.authentication.core.IdentityProviderType;
import cn.tolim.pudans.iam.communal.entity.account.po.UserIdpBindPO;
import cn.tolim.pudans.iam.communal.entity.authn.IdentityProviderEntity;
import cn.tolim.pudans.iam.communal.repository.account.UserIdpRepository;
import cn.tolim.pudans.iam.communal.repository.authentication.IdentityProviderRepository;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.core.auth.AuthenticationService;
import cn.tolim.pudans.iam.core.auth.exception.InvalidAuthRequestException;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService;
import com.alibaba.fastjson2.JSON;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class IdpQrLoginService {

    private static final String KEY_PREFIX = "pudans:iam:idp:qr:";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final Duration STATE_TTL = Duration.ofMinutes(5);

    private final IdentityProviderRepository identityProviderRepository;
    private final UserIdpRepository userIdpRepository;
    private final UserAccountService userAccountService;
    private final AuthenticationService authenticationService;
    private final IdpOAuthClient oauthClient;
    private final RedissonClient redissonClient;

    public IdpQrLoginService(IdentityProviderRepository identityProviderRepository,
                             UserIdpRepository userIdpRepository,
                             UserAccountService userAccountService,
                             AuthenticationService authenticationService,
                             IdpOAuthClient oauthClient,
                             RedissonClient redissonClient) {
        this.identityProviderRepository = identityProviderRepository;
        this.userIdpRepository = userIdpRepository;
        this.userAccountService = userAccountService;
        this.authenticationService = authenticationService;
        this.oauthClient = oauthClient;
        this.redissonClient = redissonClient;
    }

    // Creates a universal scan URL or a direct provider authorization URL
    // Parameter request: IdpQrStartRequest
    // Parameter baseUrl: String external request base URL
    // Returns: IdpQrStartResponse
    public IdpQrStartResponse start(IdpQrStartRequest request, String baseUrl) {
        String requestedType = StringUtils.trimToNull(request == null ? null : request.getType());
        String type = requestedType == null ? null : normalizeType(requestedType);
        IdentityProviderEntity provider = type == null ? null : selectProvider(type);
        if (provider == null) {
            requireAnyProvider();
        }
        String state = RandomStringUtils.randomAlphanumeric(32);
        String callbackUrl = trimTrailingSlash(baseUrl) + IamAuthApiPaths.IDP_QR_CALLBACK;
        IdpQrAttempt attempt = new IdpQrAttempt();
        attempt.setState(state);
        attempt.setRedirectUri(callbackUrl);
        attempt.setStatus(STATUS_PENDING);
        String url;
        if (provider == null) {
            url = trimTrailingSlash(baseUrl) + IamAuthApiPaths.IDP_QR_SCAN + "?state=" + state;
        } else {
            attempt.setProviderId(provider.getId());
            attempt.setProviderCode(provider.getCode());
            attempt.setType(type);
            url = oauthClient.authorizeUrl(provider, state, callbackUrl);
        }
        store(attempt, STATE_TTL);
        return IdpQrStartResponse.builder()
                .state(state)
                .url(url)
                .expiresAt(LocalDateTime.now().plus(STATE_TTL))
                .build();
    }

    // Detects the scanner app or shows a provider chooser for a universal scan URL
    // Parameter state: String login state
    // Parameter providerType: String optional requested provider type
    // Parameter userAgent: String scanner user agent
    // Returns: IdpScanResult
    public IdpScanResult scan(String state, String providerType, String userAgent) {
        if (StringUtils.isBlank(state)) {
            return IdpScanResult.html(resultPage(false, "二维码已失效，请重新扫码"));
        }
        IdpQrAttempt attempt = read(state);
        if (attempt == null) {
            return IdpScanResult.html(resultPage(false, "二维码已失效，请重新扫码"));
        }
        String type = StringUtils.trimToNull(providerType);
        if (type == null) {
            type = detectProvider(userAgent);
        }
        if (type != null) {
            try {
                IdentityProviderEntity provider = selectProvider(normalizeType(type));
                attempt.setProviderId(provider.getId());
                attempt.setProviderCode(provider.getCode());
                attempt.setType(provider.getType());
                store(attempt, STATE_TTL);
                return IdpScanResult.redirect(oauthClient.authorizeUrl(provider, state,
                        attempt.getRedirectUri()));
            } catch (InvalidAuthRequestException exception) {
                return IdpScanResult.html(providerChooser(state));
            }
        }
        return IdpScanResult.html(providerChooser(state));
    }

    // Handles the provider redirect and records the bound platform user
    // Parameter code: String provider authorization code
    // Parameter state: String login state
    // Returns: String result HTML
    public String callback(String code, String state) {
        if (StringUtils.isBlank(code) || StringUtils.isBlank(state)) {
            return resultPage(false, "扫码登录参数不完整");
        }
        IdpQrAttempt attempt = read(state);
        if (attempt == null) {
            return resultPage(false, "二维码已失效，请重新扫码");
        }
        if (STATUS_SUCCESS.equals(attempt.getStatus())) {
            return resultPage(true, "登录成功，请返回原设备继续");
        }
        if (STATUS_FAILED.equals(attempt.getStatus())) {
            return resultPage(false, StringUtils.defaultIfBlank(attempt.getMessage(),
                    "扫码登录失败，请重新扫码"));
        }
        if (StringUtils.isBlank(attempt.getProviderId())) {
            fail(attempt, "尚未选择登录方式");
            return resultPage(false, "请重新扫码并选择登录方式");
        }
        Optional<IdentityProviderEntity> provider = identityProviderRepository
                .findById(attempt.getProviderId());
        if (provider.isEmpty() || !Boolean.TRUE.equals(provider.get().getEnabled())) {
            fail(attempt, "身份提供商不可用");
            return resultPage(false, "身份提供商不可用");
        }
        try {
            IdpUserProfile profile = oauthClient.exchange(provider.get(), code,
                    attempt.getRedirectUri());
            Optional<UserIdpBindPO> binding = userIdpRepository.findByIdpIdAndOpenId(
                    attempt.getProviderId(), profile.openId());
            if (binding.isEmpty()) {
                fail(attempt, "该账号尚未绑定 PUDANS 用户");
                return resultPage(false, "该账号尚未绑定 PUDANS 用户，请联系管理员");
            }
            attempt.setStatus(STATUS_SUCCESS);
            attempt.setUserId(binding.get().getUserId());
            attempt.setMessage(null);
            store(attempt, STATE_TTL);
            return resultPage(true, "登录成功，请返回原设备继续");
        } catch (RuntimeException exception) {
            fail(attempt, "扫码登录失败，请重新扫码");
            return resultPage(false, "扫码登录失败，请重新扫码");
        }
    }

    // Polls a QR login state and consumes the completed login attempt
    // Parameter request: IdpQrPollRequest
    // Parameter ip: String
    // Parameter userAgent: String
    // Returns: IdpQrPollResponse
    public IdpQrPollResponse poll(IdpQrPollRequest request, String ip, String userAgent) {
        String state = request == null ? null : request.getState();
        if (StringUtils.isBlank(state)) {
            return pollResponse("expired", "二维码已失效", null);
        }
        IdpQrAttempt attempt = read(state);
        if (attempt == null) {
            return pollResponse("expired", "二维码已失效", null);
        }
        if (STATUS_PENDING.equals(attempt.getStatus())) {
            return pollResponse("pending", "等待扫码", null);
        }
        if (STATUS_FAILED.equals(attempt.getStatus())) {
            bucket(state).delete();
            return pollResponse("failed", attempt.getMessage(), null);
        }
        if (!bucket(state).delete()) {
            return pollResponse("expired", "二维码已失效", null);
        }
        try {
            UserAccountService.AuthenticatedAccount account =
                    userAccountService.requireAccountById(attempt.getUserId());
            LoginResponse login = authenticationService.completeLogin(account, null, null, ip,
                    userAgent);
            return pollResponse(STATUS_SUCCESS, "登录成功", login);
        } catch (RuntimeException exception) {
            return pollResponse("failed", "账号不可用，请联系管理员", null);
        }
    }

    private void requireAnyProvider() {
        if (availableProviders().isEmpty()) {
            throw new InvalidAuthRequestException("idp_not_configured",
                    "No identity provider is configured for QR login");
        }
    }

    private String detectProvider(String userAgent) {
        String value = StringUtils.lowerCase(StringUtils.defaultString(userAgent));
        if (value.contains("dingtalk")) {
            return IdentityProviderType.DINGTALK_OAUTH.value();
        }
        if (value.contains("feishu") || value.contains("lark")) {
            return IdentityProviderType.FEISHU_OAUTH.value();
        }
        return null;
    }

    private List<String> availableProviders() {
        return List.of(IdentityProviderType.DINGTALK_OAUTH.value(),
                        IdentityProviderType.FEISHU_OAUTH.value()).stream()
                .filter(this::isConfigured)
                .toList();
    }

    private boolean isConfigured(String type) {
        List<IdentityProviderEntity> providers = identityProviderRepository.findByType(type);
        return providers != null && providers.stream()
                .anyMatch(provider -> Boolean.TRUE.equals(provider.getEnabled())
                        && Boolean.TRUE.equals(provider.getDisplayed()));
    }

    private String providerChooser(String state) {
        List<String> types = availableProviders();
        if (types.isEmpty()) {
            return resultPage(false, "尚未配置钉钉或飞书身份源");
        }
        StringBuilder links = new StringBuilder();
        for (String type : types) {
            String name = IdentityProviderType.DINGTALK_OAUTH.value().equals(type)
                    ? "钉钉" : "飞书";
            links.append("<a style=\"display:inline-block;margin:8px;padding:10px 28px;"
                    + "border-radius:6px;background:#1677ff;color:#fff;text-decoration:none\""
                    + " href=\"?state=").append(state).append("&provider=").append(type)
                    .append("\">").append(name).append("扫码登录</a>");
        }
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>PUDANS 扫码登录</title></head>"
                + "<body style=\"margin:0;min-height:100vh;display:grid;place-items:center;"
                + "font-family:system-ui,sans-serif;background:#f5f7ff;color:#1f2356\">"
                + "<div style=\"padding:32px;text-align:center\">"
                + "<p style=\"font-size:17px;margin:0 0 8px\">请选择扫码登录方式</p>"
                + links + "</div></body></html>";
    }

    private String normalizeType(String type) {
        String value = StringUtils.trimToEmpty(type).toLowerCase(Locale.ROOT);
        if (IdentityProviderType.DINGTALK_OAUTH.value().equals(value)
                || IdentityProviderType.FEISHU_OAUTH.value().equals(value)) {
            return value;
        }
        throw new InvalidAuthRequestException("idp_type_invalid",
                "Only DingTalk and Feishu QR login are supported");
    }

    private IdentityProviderEntity selectProvider(String type) {
        List<IdentityProviderEntity> providers = identityProviderRepository.findByType(type);
        if (providers == null) {
            throw new InvalidAuthRequestException("idp_not_configured",
                    "No identity provider is configured for this login method");
        }
        return providers.stream()
                .filter(provider -> Boolean.TRUE.equals(provider.getEnabled())
                        && Boolean.TRUE.equals(provider.getDisplayed()))
                .findFirst()
                .orElseThrow(() -> new InvalidAuthRequestException("idp_not_configured",
                        "No identity provider is configured for this login method"));
    }

    private void fail(IdpQrAttempt attempt, String message) {
        attempt.setStatus(STATUS_FAILED);
        attempt.setMessage(message);
        store(attempt, STATE_TTL);
    }

    private void store(IdpQrAttempt attempt, Duration ttl) {
        bucket(attempt.getState()).set(JSON.toJSONString(attempt), ttl);
    }

    private IdpQrAttempt read(String state) {
        String value = bucket(state).get();
        return StringUtils.isBlank(value) ? null : JSON.parseObject(value, IdpQrAttempt.class);
    }

    private RBucket<String> bucket(String state) {
        return redissonClient.getBucket(KEY_PREFIX + state);
    }

    private IdpQrPollResponse pollResponse(String status, String message, LoginResponse login) {
        return IdpQrPollResponse.builder()
                .status(status)
                .message(message)
                .login(login)
                .build();
    }

    private String trimTrailingSlash(String value) {
        String result = StringUtils.defaultString(value).trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    // Result of resolving a universal scan URL
    public record IdpScanResult(String redirectUrl, String html) {

        public static IdpScanResult redirect(String redirectUrl) {
            return new IdpScanResult(redirectUrl, null);
        }

        public static IdpScanResult html(String html) {
            return new IdpScanResult(null, html);
        }
    }

    private String resultPage(boolean success, String message) {
        String color = success ? "#16a34a" : "#dc2626";
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>PUDANS 扫码登录</title></head>"
                + "<body style=\"margin:0;min-height:100vh;display:grid;place-items:center;"
                + "font-family:system-ui,sans-serif;background:#f5f7ff;color:#1f2356\">"
                + "<div style=\"padding:32px;text-align:center\">"
                + "<p style=\"font-size:32px;margin:0 0 12px;color:" + color + "\">"
                + (success ? "&#10003;" : "&#10005;") + "</p>"
                + "<p style=\"font-size:16px;line-height:1.7\">" + message + "</p>"
                + "</div></body></html>";
    }
}