package cn.tolim.pudans.iam.authentication.mail.configurer;

import cn.tolim.pudans.iam.authentication.mail.filter.MailOtpAuthenticationFilter;
import cn.tolim.pudans.iam.authentication.mail.filter.SendMailOtpFilter;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.core.security.otp.OtpContextHelp;
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


public class MailOtpAuthenticationConfigurer extends
        AbstractAuthenticationFilterConfigurer<HttpSecurity, MailOtpAuthenticationConfigurer, MailOtpAuthenticationFilter> {

    private final UserRepository userRepository;
    private final UserDetailsService userDetailsService;
    private final OtpContextHelp otpContextHelp;
    @Setter
    private String loginProcessingUrl = MailOtpAuthenticationFilter.DEFAULT_FILTER_PROCESSES_URI;
    public MailOtpAuthenticationConfigurer(UserRepository userRepository,
                                           UserDetailsService userDetailsService,
                                           OtpContextHelp otpContextHelp) {
        Assert.notNull(userDetailsService, "userRepository 不能为空");
        Assert.notNull(userDetailsService, "userDetailsService 不能为空");
        Assert.notNull(otpContextHelp, "otpContextHelp 不能为空");
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.otpContextHelp = otpContextHelp;
    }

    public static MailOtpAuthenticationConfigurer mailOtp(UserRepository userRepository,
                                                          UserDetailsService userDetailsService,
                                                          OtpContextHelp otpContextHelp) {
        return new MailOtpAuthenticationConfigurer(userRepository, userDetailsService,
                otpContextHelp);
    }

    @Override
    public void init(HttpSecurity http) {
        // Email OTP sending
        http.addFilterBefore(new SendMailOtpFilter(userRepository, otpContextHelp),
                OAuth2LoginAuthenticationFilter.class);
        // Email OTP authentication
        this.setAuthenticationFilter(
                new MailOtpAuthenticationFilter(userDetailsService, otpContextHelp));
        putFilterAfter(http, this.getAuthenticationFilter(), SendMailOtpFilter.class);

        // Login processing URL
        super.loginProcessingUrl(this.loginProcessingUrl);
        super.init(http);
    }

    public RequestMatcher getRequestMatcher() {
        return MailOtpAuthenticationFilter.getRequestMatcher();
    }

    // Create the RequestMatcher given a loginProcessingUrl
    @Override
    protected RequestMatcher createLoginProcessingUrlMatcher(String loginProcessingUrl) {
        return new AntPathRequestMatcher(loginProcessingUrl, HttpMethod.POST.name());
    }
}
