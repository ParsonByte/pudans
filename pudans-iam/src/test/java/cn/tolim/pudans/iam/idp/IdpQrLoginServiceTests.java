package cn.tolim.pudans.iam.idp;

import cn.tolim.pudans.contract.iam.dto.auth.IdpQrPollRequest;
import cn.tolim.pudans.contract.iam.dto.auth.IdpQrPollResponse;
import cn.tolim.pudans.contract.iam.dto.auth.IdpQrStartRequest;
import cn.tolim.pudans.contract.iam.dto.auth.IdpQrStartResponse;
import cn.tolim.pudans.contract.iam.dto.auth.LoginResponse;
import cn.tolim.pudans.iam.communal.entity.account.po.UserIdpBindPO;
import cn.tolim.pudans.iam.communal.entity.authn.IdentityProviderEntity;
import cn.tolim.pudans.iam.communal.repository.account.UserIdpRepository;
import cn.tolim.pudans.iam.communal.repository.authentication.IdentityProviderRepository;
import cn.tolim.pudans.iam.core.auth.AuthenticationService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService.AuthenticatedAccount;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdpQrLoginServiceTests {

    private static final String PROVIDER_ID = "idp-1";
    private static final String STATE = "state-1";

    @Mock
    private IdentityProviderRepository identityProviderRepository;

    @Mock
    private UserIdpRepository userIdpRepository;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private IdpOAuthClient oauthClient;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> bucket;

    @Test
    void startsQrLoginForConfiguredProvider() {
        IdentityProviderEntity provider = provider();
        when(identityProviderRepository.findByType("dingtalk_oauth"))
                .thenReturn(List.of(provider));
        when(oauthClient.authorizeUrl(eq(provider), anyString(),
                contains("/iam/auth/idp/qr/callback")))
                .thenReturn("https://login.dingtalk.com/oauth2/auth?state=state-1");
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);

        IdpQrStartResponse response = service().start(IdpQrStartRequest.builder()
                .type("dingtalk_oauth").build(), "http://localhost:8080/");

        assertThat(response.getState()).isNotBlank();
        assertThat(response.getUrl()).contains("dingtalk");
        assertThat(response.getExpiresAt()).isNotNull();
        verify(bucket).set(anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void startsUniversalQrWhenAnyProviderIsConfigured() {
        when(identityProviderRepository.findByType("dingtalk_oauth"))
                .thenReturn(List.of(provider()));
        when(identityProviderRepository.findByType("feishu_oauth"))
                .thenReturn(List.of());
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);

        IdpQrStartResponse response = service().start(IdpQrStartRequest.builder().build(),
                "http://localhost:8080");

        assertThat(response.getUrl()).contains("/auth/idp/qr/scan");
        assertThat(response.getState()).isNotBlank();
    }

    @Test
    void scanRedirectsToTheScannerProvider() {
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn(JSON.toJSONString(pendingAttempt()));
        IdentityProviderEntity feishu = provider();
        feishu.setId("idp-2");
        feishu.setCode("feishu");
        feishu.setType("feishu_oauth");
        when(identityProviderRepository.findByType("feishu_oauth"))
                .thenReturn(List.of(feishu));
        when(oauthClient.authorizeUrl(eq(feishu), eq(STATE), anyString()))
                .thenReturn("https://passport.feishu.cn/oauth?state=state-1");

        IdpQrLoginService.IdpScanResult result = service().scan(STATE, null,
                "Mozilla Lark/7.0");

        assertThat(result.redirectUrl()).contains("feishu");
        assertThat(result.html()).isNull();
    }

    @Test
    void reportsPendingQrLogin() {
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn(JSON.toJSONString(pendingAttempt()));

        IdpQrLoginService service = service();
        IdpQrPollResponse response = service.poll(IdpQrPollRequest.builder()
                .state(STATE).build(), "127.0.0.1", "test");

        assertThat(response.getStatus()).isEqualTo("pending");
        assertThat(response.getLogin()).isNull();
    }

    @Test
    void consumesSuccessfulQrLogin() {
        IdpQrAttempt attempt = pendingAttempt();
        attempt.setStatus("success");
        attempt.setUserId("user-1");
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn(JSON.toJSONString(attempt));
        when(bucket.delete()).thenReturn(true);
        AuthenticatedAccount account = new AuthenticatedAccount("user-1", "alice", "user",
                "org-1", "Alice");
        when(userAccountService.requireAccountById("user-1")).thenReturn(account);
        LoginResponse login = new LoginResponse();
        login.setSid("sid-1");
        when(authenticationService.completeLogin(account, null, null, "127.0.0.1", "test"))
                .thenReturn(login);

        IdpQrPollResponse response = service().poll(IdpQrPollRequest.builder()
                .state(STATE).build(), "127.0.0.1", "test");

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getLogin().getSid()).isEqualTo("sid-1");
        verify(bucket).delete();
    }

    @Test
    void callbackRecordsBoundUser() {
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn(JSON.toJSONString(pendingAttempt()));
        when(identityProviderRepository.findById(PROVIDER_ID))
                .thenReturn(Optional.of(provider()));
        when(oauthClient.exchange(any(), eq("code-1"), anyString()))
                .thenReturn(new IdpUserProfile("open-1", "union-1", "alice@example.com",
                        null, "Alice", null));
        UserIdpBindPO binding = new UserIdpBindPO("bind-1", "user-1", "alice", "Alice",
                "alice@example.com", null, null, null, "open-1", "union-1", PROVIDER_ID,
                "DingTalk", "dingtalk_oauth", null);
        when(userIdpRepository.findByIdpIdAndOpenId(PROVIDER_ID, "open-1"))
                .thenReturn(Optional.of(binding));

        String html = service().callback("code-1", STATE);

        assertThat(html).contains("登录成功");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(bucket).set(captor.capture(), eq(Duration.ofMinutes(5)));
        IdpQrAttempt saved = JSON.parseObject(captor.getValue(), IdpQrAttempt.class);
        assertThat(saved.getStatus()).isEqualTo("success");
        assertThat(saved.getUserId()).isEqualTo("user-1");
    }

    @Test
    void callbackRejectsUnboundProviderUser() {
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn(JSON.toJSONString(pendingAttempt()));
        when(identityProviderRepository.findById(PROVIDER_ID))
                .thenReturn(Optional.of(provider()));
        when(oauthClient.exchange(any(), eq("code-1"), anyString()))
                .thenReturn(new IdpUserProfile("open-1", "union-1", null, null, "Alice", null));
        when(userIdpRepository.findByIdpIdAndOpenId(PROVIDER_ID, "open-1"))
                .thenReturn(Optional.empty());

        String html = service().callback("code-1", STATE);

        assertThat(html).contains("尚未绑定");
    }

    private IdpQrLoginService service() {
        return new IdpQrLoginService(identityProviderRepository, userIdpRepository,
                userAccountService, authenticationService, oauthClient, redissonClient);
    }

    private IdentityProviderEntity provider() {
        IdentityProviderEntity provider = new IdentityProviderEntity();
        provider.setId(PROVIDER_ID);
        provider.setCode("dingtalk");
        provider.setType("dingtalk_oauth");
        provider.setEnabled(Boolean.TRUE);
        provider.setDisplayed(Boolean.TRUE);
        provider.setConfig("{\"appKey\":\"key\",\"appSecret\":\"secret\"}");
        return provider;
    }

    private IdpQrAttempt pendingAttempt() {
        IdpQrAttempt attempt = new IdpQrAttempt();
        attempt.setState(STATE);
        attempt.setProviderId(PROVIDER_ID);
        attempt.setProviderCode("dingtalk");
        attempt.setType("dingtalk_oauth");
        attempt.setRedirectUri("http://localhost:8080/iam/auth/idp/qr/callback");
        attempt.setStatus("pending");
        return attempt;
    }
}