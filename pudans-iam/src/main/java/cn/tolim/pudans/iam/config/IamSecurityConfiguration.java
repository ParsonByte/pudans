package cn.tolim.pudans.iam.config;

import cn.tolim.pudans.communal.core.security.JwtTokenService;
import cn.tolim.pudans.communal.security.PudansJwtProperties;
import cn.tolim.pudans.communal.security.RestAuthenticationEntryPoint;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.contract.IamApiPaths;
import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.session.GlobalSessionService;
import cn.tolim.pudans.iam.core.auth.user.ApplicationAccessService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService;
import cn.tolim.pudans.iam.security.IamJwtAuthenticationFilter;
import cn.tolim.pudans.iam.security.IamSessionAuthenticationFilter;
import cn.tolim.pudans.iam.security.IamSessionCookieService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableScheduling
@EnableConfigurationProperties(PudansJwtProperties.class)
public class IamSecurityConfiguration {

    // JWT service kept for gateway and management service callers
    // Parameter properties: PudansJwtProperties
    // Returns: JwtTokenService
    @Bean
    public JwtTokenService jwtTokenService(PudansJwtProperties properties) {
        return new JwtTokenService(
                properties.getSecret(),
                properties.getIssuer(),
                properties.getAudience(),
                properties.getTtlSeconds());
    }

    // Password encoder supporting both prefixed and legacy bcrypt hashes
    // Returns: PasswordEncoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder encoder = (DelegatingPasswordEncoder) PasswordEncoderFactories
                .createDelegatingPasswordEncoder();
        encoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return encoder;
    }

    // Resolves browser global login sessions from the session cookie or internal header
    // Parameter globalSessionService: GlobalSessionService
    // Parameter userAccountService: UserAccountService
    // Parameter applicationAccessService: ApplicationAccessService
    // Parameter cookieService: IamSessionCookieService
    // Returns: IamSessionAuthenticationFilter
    @Bean
    public IamSessionAuthenticationFilter iamSessionAuthenticationFilter(
            GlobalSessionService globalSessionService,
            UserAccountService userAccountService,
            ApplicationAccessService applicationAccessService,
            IamSessionCookieService cookieService) {
        return new IamSessionAuthenticationFilter(globalSessionService, userAccountService,
                applicationAccessService, cookieService);
    }

    // JWT filter that defers to an already authenticated browser session
    // Parameter jwtTokenService: JwtTokenService
    // Parameter objectMapper: ObjectMapper
    // Returns: IamJwtAuthenticationFilter
    @Bean
    public IamJwtAuthenticationFilter iamJwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                                                 ObjectMapper objectMapper) {
        return new IamJwtAuthenticationFilter(jwtTokenService, objectMapper);
    }

    // Prevents the servlet container from registering the session filter twice
    // Parameter filter: IamSessionAuthenticationFilter
    // Returns: FilterRegistrationBean
    @Bean
    public FilterRegistrationBean<IamSessionAuthenticationFilter> iamSessionFilterRegistration(
            IamSessionAuthenticationFilter filter) {
        FilterRegistrationBean<IamSessionAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    // Prevents the servlet container from registering the JWT filter twice
    // Parameter filter: IamJwtAuthenticationFilter
    // Returns: FilterRegistrationBean
    @Bean
    public FilterRegistrationBean<IamJwtAuthenticationFilter> iamJwtFilterRegistration(
            IamJwtAuthenticationFilter filter) {
        FilterRegistrationBean<IamJwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    // IAM security filter chain: public login, ticket and session validation endpoints,
    // browser session authentication and gateway JWT fallback for management APIs
    // Parameter http: HttpSecurity
    // Parameter sessionFilter: IamSessionAuthenticationFilter
    // Parameter jwtFilter: IamJwtAuthenticationFilter
    // Parameter authProperties: IamAuthProperties
    // Parameter objectMapper: ObjectMapper
    // Returns: SecurityFilterChain
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   IamSessionAuthenticationFilter sessionFilter,
                                                   IamJwtAuthenticationFilter jwtFilter,
                                                   IamAuthProperties authProperties,
                                                   ObjectMapper objectMapper) throws Exception {
        RestAuthenticationEntryPoint authenticationEntryPoint = new RestAuthenticationEntryPoint(objectMapper);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(authProperties)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(IamApiPaths.HEALTH, IamApiPaths.HEALTH + "/**", IamApiPaths.INFO)
                        .permitAll()
                        .requestMatchers(IamApiPaths.ACTUATOR + "/metrics",
                                IamApiPaths.ACTUATOR + "/metrics/**",
                                IamApiPaths.ACTUATOR + "/prometheus",
                                IamApiPaths.ACTUATOR + "/prometheus/**").permitAll()
                        .requestMatchers(IamApiPaths.ERROR, IamApiPaths.ERROR + "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, IamAuthApiPaths.LOGIN_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, IamAuthApiPaths.LOGIN_CONFIG_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, IamAuthApiPaths.LOGOUT_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, IamAuthApiPaths.MFA_CHALLENGE_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, IamAuthApiPaths.MFA_VERIFY_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, IamAuthApiPaths.MFA_WEBAUTHN_OPTIONS_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, IamAuthApiPaths.MFA_WEBAUTHN_VERIFY_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, IamAuthApiPaths.SESSION_VALIDATE_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, IamAuthApiPaths.SESSION_TOUCH_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, IamAuthApiPaths.TICKET_ISSUE_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, IamAuthApiPaths.TICKET_EXCHANGE_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, IamAuthApiPaths.PORTAL,
                                IamAuthApiPaths.PORTAL + "/**").permitAll()
                        .requestMatchers(IamAuthApiPaths.BASE + "/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(sessionFilter, IamJwtAuthenticationFilter.class)
                .logout(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    // CORS configuration for the separate homepage and login page origins
    // Parameter properties: IamAuthProperties
    // Returns: CorsConfigurationSource
    @Bean
    public CorsConfigurationSource corsConfigurationSource(IamAuthProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        if (properties.getCors().getAllowedOrigins() != null
                && !properties.getCors().getAllowedOrigins().isEmpty()) {
            configuration.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        }
        if (properties.getCors().getAllowedOriginPatterns() != null
                && !properties.getCors().getAllowedOriginPatterns().isEmpty()) {
            configuration.setAllowedOriginPatterns(properties.getCors().getAllowedOriginPatterns());
        }
        configuration.setAllowedMethods(properties.getCors().getAllowedMethods());
        configuration.setAllowedHeaders(properties.getCors().getAllowedHeaders());
        configuration.setAllowCredentials(Boolean.TRUE.equals(properties.getCors().getAllowCredentials()));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}