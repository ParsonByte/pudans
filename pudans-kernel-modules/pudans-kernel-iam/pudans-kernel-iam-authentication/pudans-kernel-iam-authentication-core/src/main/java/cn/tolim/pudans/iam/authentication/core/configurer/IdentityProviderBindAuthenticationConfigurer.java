package cn.tolim.pudans.iam.authentication.core.configurer;

import cn.tolim.pudans.iam.authentication.core.IdentityProviderAuthenticationService;
import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderAuthenticationProvider;
import cn.tolim.pudans.iam.authentication.core.filter.IdentityProviderBindUserAuthenticationFilter;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;

import java.util.Objects;

import static cn.tolim.pudans.iam.authentication.core.filter.IdentityProviderBindUserAuthenticationFilter.DEFAULT_FILTER_PROCESSES_URI;
import static cn.tolim.pudans.iam.infra.security.util.HttpSecurityConfigUtils.getUserDetailsService;
import static cn.tolim.pudans.iam.infra.security.util.HttpSecurityFilterOrderRegistrationUtils.putFilterBefore;


public class IdentityProviderBindAuthenticationConfigurer extends
        AbstractAuthenticationFilterConfigurer<HttpSecurity, IdentityProviderBindAuthenticationConfigurer, IdentityProviderBindUserAuthenticationFilter> {

    private final IdentityProviderAuthenticationService identityProviderAuthenticationService;
    private final PasswordEncoder passwordEncoder;
    private UserDetailsService userDetailsService;

    // Creates a new instance with minimal defaults
    public IdentityProviderBindAuthenticationConfigurer(UserDetailsService userDetailsService,
                                                        IdentityProviderAuthenticationService identityProviderAuthenticationService,
                                                        PasswordEncoder passwordEncoder) {
        super(new IdentityProviderBindUserAuthenticationFilter(), DEFAULT_FILTER_PROCESSES_URI);
        Assert.notNull(userDetailsService, "userDetailsService 不能为空");
        Assert.notNull(identityProviderAuthenticationService, "userIdpService 不能为空");
        Assert.notNull(identityProviderAuthenticationService, "passwordEncoder 不能为空");
        this.identityProviderAuthenticationService = identityProviderAuthenticationService;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
    }

    // Creates a new instance with minimal defaults
    public IdentityProviderBindAuthenticationConfigurer(IdentityProviderAuthenticationService identityProviderAuthenticationService,
                                                        PasswordEncoder passwordEncoder) {
        super(new IdentityProviderBindUserAuthenticationFilter(), DEFAULT_FILTER_PROCESSES_URI);
        Assert.notNull(identityProviderAuthenticationService, "userIdpService 不能为空");
        Assert.notNull(identityProviderAuthenticationService, "passwordEncoder 不能为空");
        this.identityProviderAuthenticationService = identityProviderAuthenticationService;
        this.passwordEncoder = passwordEncoder;
    }

    public static IdentityProviderBindAuthenticationConfigurer idpBind(UserDetailsService userDetailsService,
                                                                       IdentityProviderAuthenticationService identityProviderAuthenticationService,
                                                                       PasswordEncoder passwordEncoder) {
        return new IdentityProviderBindAuthenticationConfigurer(userDetailsService,
                identityProviderAuthenticationService, passwordEncoder);
    }

    public static IdentityProviderBindAuthenticationConfigurer idpBind(IdentityProviderAuthenticationService identityProviderAuthenticationService,
                                                                       PasswordEncoder passwordEncoder) {
        return new IdentityProviderBindAuthenticationConfigurer(
                identityProviderAuthenticationService, passwordEncoder);
    }

    @Override
    public void init(HttpSecurity http) {
        super.init(http);
        putFilterBefore(http, getAuthenticationFilter(), OAuth2LoginAuthenticationFilter.class);
        // Authentication provider
        if (Objects.isNull(userDetailsService)) {
            userDetailsService = getUserDetailsService(http);
        }
        http.authenticationProvider(new IdentityProviderAuthenticationProvider(userDetailsService,
                identityProviderAuthenticationService, passwordEncoder));
    }

    // Create the RequestMatcher given a loginProcessingUrl
    @Override
    protected RequestMatcher createLoginProcessingUrlMatcher(String loginProcessingUrl) {
        return new AntPathRequestMatcher(loginProcessingUrl);
    }
}
