package cn.tolim.pudans.iam.authentication.sms.configurer;

import cn.tolim.pudans.iam.authentication.sms.filter.SendSmsOtpFilter;
import cn.tolim.pudans.iam.authentication.sms.filter.SmsOtpAuthenticationFilter;
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


public class SmsOtpAuthenticationConfigurer extends
        AbstractAuthenticationFilterConfigurer<HttpSecurity, SmsOtpAuthenticationConfigurer, SmsOtpAuthenticationFilter> {
    private final UserRepository userRepository;
    private final UserDetailsService userDetailsService;
    private final OtpContextHelp otpContextHelp;
    @Setter
    private String loginProcessingUrl = SmsOtpAuthenticationFilter.DEFAULT_FILTER_PROCESSES_URI;
    public SmsOtpAuthenticationConfigurer(UserRepository userRepository,
                                          UserDetailsService userDetailsService,
                                          OtpContextHelp otpContextHelp) {
        Assert.notNull(userDetailsService, "userRepository 不能为空");
        Assert.notNull(userDetailsService, "userDetailsService 不能为空");
        Assert.notNull(otpContextHelp, "otpContextHelp 不能为空");
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.otpContextHelp = otpContextHelp;
    }

    public static SmsOtpAuthenticationConfigurer smsOtp(UserRepository userRepository,
                                                        UserDetailsService userDetailsService,
                                                        OtpContextHelp otpContextHelp) {
        return new SmsOtpAuthenticationConfigurer(userRepository, userDetailsService,
                otpContextHelp);
    }

    @Override
    public void init(HttpSecurity http) {
        http.addFilterBefore(new SendSmsOtpFilter(userRepository, otpContextHelp),
                OAuth2LoginAuthenticationFilter.class);

        // OTP
        this.setAuthenticationFilter(
                new SmsOtpAuthenticationFilter(userDetailsService, otpContextHelp));
        putFilterAfter(http, this.getAuthenticationFilter(), SendSmsOtpFilter.class);

        // Login processing URL
        super.loginProcessingUrl(this.loginProcessingUrl);
        super.init(http);
    }

    public RequestMatcher getRequestMatcher() {
        return SendSmsOtpFilter.getRequestMatcher();
    }

    // Create the RequestMatcher given a loginProcessingUrl
    @Override
    protected RequestMatcher createLoginProcessingUrlMatcher(String loginProcessingUrl) {
        return new AntPathRequestMatcher(loginProcessingUrl, HttpMethod.POST.name());
    }
}
