package cn.tolim.pudans.iam.authentication.dingtalk.configurer;

import cn.tolim.pudans.iam.authentication.core.IdentityProviderAuthenticationService;
import cn.tolim.pudans.iam.authentication.core.client.RegisteredIdentityProviderClientRepository;
import cn.tolim.pudans.iam.authentication.dingtalk.filter.DingtalkOAuth2AuthenticationFilter;
import cn.tolim.pudans.iam.authentication.dingtalk.filter.DingtalkOAuth2AuthorizationRequestRedirectFilter;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import lombok.Setter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;

import static cn.tolim.pudans.iam.infra.security.util.HttpSecurityFilterOrderRegistrationUtils.putFilterBefore;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public final class DingTalkAuthenticationConfigurer extends
        AbstractAuthenticationFilterConfigurer<HttpSecurity, DingTalkAuthenticationConfigurer, DingtalkOAuth2AuthenticationFilter> {
    private final RegisteredIdentityProviderClientRepository registeredIdentityProviderClientRepository;
    private final IdentityProviderAuthenticationService identityProviderAuthenticationService;
    @Setter
    private String loginProcessingUrl = DingtalkOAuth2AuthenticationFilter.DEFAULT_FILTER_PROCESSES_URI;

    public DingTalkAuthenticationConfigurer(RegisteredIdentityProviderClientRepository registeredIdentityProviderClientRepository,
                                            IdentityProviderAuthenticationService identityProviderAuthenticationService) {
        Assert.notNull(registeredIdentityProviderClientRepository,
                "registeredIdentityProviderClientRepository 不能为空");
        Assert.notNull(identityProviderAuthenticationService, "userIdpService 不能为空");
        this.registeredIdentityProviderClientRepository = registeredIdentityProviderClientRepository;
        this.identityProviderAuthenticationService = identityProviderAuthenticationService;
    }

    public static DingTalkAuthenticationConfigurer dingTalkOAuth2(RegisteredIdentityProviderClientRepository registeredIdentityProviderClientRepository,
                                                                  IdentityProviderAuthenticationService identityProviderAuthenticationService) {
        return new DingTalkAuthenticationConfigurer(registeredIdentityProviderClientRepository,
                identityProviderAuthenticationService);
    }

    // Create the RequestMatcher given a loginProcessingUrl
    @Override
    protected RequestMatcher createLoginProcessingUrlMatcher(String loginProcessingUrl) {
        return new AntPathRequestMatcher(loginProcessingUrl, HttpMethod.GET.name());
    }

    @Override
    public void init(HttpSecurity http) {
        // DingTalk login authentication
        this.setAuthenticationFilter(new DingtalkOAuth2AuthenticationFilter(
                registeredIdentityProviderClientRepository, identityProviderAuthenticationService));
        putFilterBefore(http, this.getAuthenticationFilter(),
                OAuth2LoginAuthenticationFilter.class);

        // DingTalk request redirect
        http.addFilterBefore(
                new DingtalkOAuth2AuthorizationRequestRedirectFilter(
                        registeredIdentityProviderClientRepository),
                OAuth2AuthorizationRequestRedirectFilter.class);

        // Login processing URL
        super.loginProcessingUrl(loginProcessingUrl);
        super.init(http);
    }

    public RequestMatcher getRequestMatcher() {
        return new OrRequestMatcher(
                DingtalkOAuth2AuthorizationRequestRedirectFilter.getRequestMatcher(),
                DingtalkOAuth2AuthenticationFilter.getRequestMatcher());
    }
}
