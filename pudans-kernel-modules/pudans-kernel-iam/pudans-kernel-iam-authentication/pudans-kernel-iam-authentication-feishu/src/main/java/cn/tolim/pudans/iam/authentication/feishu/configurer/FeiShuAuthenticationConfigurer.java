package cn.tolim.pudans.iam.authentication.feishu.configurer;

import cn.tolim.pudans.iam.authentication.core.IdentityProviderAuthenticationService;
import cn.tolim.pudans.iam.authentication.core.client.RegisteredIdentityProviderClientRepository;
import cn.tolim.pudans.iam.authentication.feishu.filter.FeiShuAuthorizationRequestRedirectFilter;
import cn.tolim.pudans.iam.authentication.feishu.filter.FeiShuLoginAuthenticationFilter;
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


public final class FeiShuAuthenticationConfigurer extends
        AbstractAuthenticationFilterConfigurer<HttpSecurity, FeiShuAuthenticationConfigurer, FeiShuLoginAuthenticationFilter> {
    private final RegisteredIdentityProviderClientRepository registeredIdentityProviderClientRepository;
    private final IdentityProviderAuthenticationService identityProviderAuthenticationService;
    @Setter
    private String loginProcessingUrl = FeiShuLoginAuthenticationFilter.DEFAULT_FILTER_PROCESSES_URI;

    public FeiShuAuthenticationConfigurer(RegisteredIdentityProviderClientRepository registeredIdentityProviderClientRepository,
                                          IdentityProviderAuthenticationService identityProviderAuthenticationService) {
        Assert.notNull(registeredIdentityProviderClientRepository,
                "registeredIdentityProviderClientRepository 不能为空");
        Assert.notNull(identityProviderAuthenticationService, "userIdpService 不能为空");
        this.registeredIdentityProviderClientRepository = registeredIdentityProviderClientRepository;
        this.identityProviderAuthenticationService = identityProviderAuthenticationService;
    }

    public static FeiShuAuthenticationConfigurer feiShuOAuth2(RegisteredIdentityProviderClientRepository registeredIdentityProviderClientRepository,
                                                              IdentityProviderAuthenticationService identityProviderAuthenticationService) {
        return new FeiShuAuthenticationConfigurer(registeredIdentityProviderClientRepository,
                identityProviderAuthenticationService);
    }

    // Create the RequestMatcher given a loginProcessingUrl
    @Override
    protected RequestMatcher createLoginProcessingUrlMatcher(String loginProcessingUrl) {
        return new AntPathRequestMatcher(loginProcessingUrl, HttpMethod.GET.name());
    }

    @Override
    public void init(HttpSecurity http) {
        // Feishu login authentication
        this.setAuthenticationFilter(new FeiShuLoginAuthenticationFilter(
                registeredIdentityProviderClientRepository, identityProviderAuthenticationService));
        putFilterBefore(http, this.getAuthenticationFilter(),
                OAuth2LoginAuthenticationFilter.class);

        // Feishu request redirect
        http.addFilterBefore(
                new FeiShuAuthorizationRequestRedirectFilter(
                        registeredIdentityProviderClientRepository),
                OAuth2AuthorizationRequestRedirectFilter.class);

        // Login processing URL
        super.loginProcessingUrl(this.loginProcessingUrl);
        super.init(http);
    }

    public RequestMatcher getRequestMatcher() {
        return new OrRequestMatcher(FeiShuAuthorizationRequestRedirectFilter.getRequestMatcher(),
                FeiShuLoginAuthenticationFilter.getRequestMatcher());
    }
}
