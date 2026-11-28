package cn.tolim.pudans.iam.authentication.totp.configurer;

import cn.tolim.pudans.iam.authentication.totp.filter.TotpAuthenticationFilter;
import cn.tolim.pudans.iam.authentication.totp.service.TotpService;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import lombok.Setter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;

import static cn.tolim.pudans.iam.infra.security.util.HttpSecurityFilterOrderRegistrationUtils.putFilterAfter;

// Registers the TOTP login filter with the IAM security chain
public class TotpAuthenticationConfigurer extends
        AbstractAuthenticationFilterConfigurer<HttpSecurity, TotpAuthenticationConfigurer, TotpAuthenticationFilter> {

    private final UserDetailsService userDetailsService;
    private final TotpService totpService;
    @Setter
    private String loginProcessingUrl = TotpAuthenticationFilter.DEFAULT_FILTER_PROCESSES_URI;

    public TotpAuthenticationConfigurer(UserDetailsService userDetailsService,
                                        TotpService totpService) {
        Assert.notNull(userDetailsService, "userDetailsService must not be null");
        Assert.notNull(totpService, "totpService must not be null");
        this.userDetailsService = userDetailsService;
        this.totpService = totpService;
    }

    public static TotpAuthenticationConfigurer totp(UserDetailsService userDetailsService,
                                                    TotpService totpService) {
        return new TotpAuthenticationConfigurer(userDetailsService, totpService);
    }

    @Override
    public void init(HttpSecurity http) {
        this.setAuthenticationFilter(
                new TotpAuthenticationFilter(userDetailsService, totpService));
        putFilterAfter(http, this.getAuthenticationFilter(),
                OAuth2LoginAuthenticationFilter.class);
        super.loginProcessingUrl(this.loginProcessingUrl);
        super.init(http);
    }

    public RequestMatcher getRequestMatcher() {
        return TotpAuthenticationFilter.TOTP_LOGIN_MATCHER;
    }

    @Override
    protected RequestMatcher createLoginProcessingUrlMatcher(String loginProcessingUrl) {
        return new AntPathRequestMatcher(loginProcessingUrl, HttpMethod.POST.name());
    }
}