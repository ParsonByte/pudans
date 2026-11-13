package cn.tolim.pudans.infra.properties;

import static org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.CorsEndpointProperties;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode;

@Data
public class SecurityProperties {

    private final FrameOptions frameOptions = new FrameOptions();

    private final ReferrerOptions referrerOptions = new ReferrerOptions();

    private final CorsOptions corsOptions = new CorsOptions();

    private final RememberMeOptions rememberMe = new RememberMeOptions();

    private final TwoFactorAuthOptions twoFactorAuth = new TwoFactorAuthOptions();

    private final TotpOptions totp = new TotpOptions();

    private final BasicAuthOptions basicAuth = new BasicAuthOptions();

    private final List<PasswordResetMethod> passwordResetMethods = new ArrayList<>();

    private final PasswordResetOptions passwordReset = new PasswordResetOptions();

    private final List<String> trustedProxies = new ArrayList<>();

    /**
     * Destination used after a successful interactive sign-in. Deployments can
     * point this at an external application or keep the bundled auth landing
     * page, which confirms the active session.
     */
    private String postLoginUrl = "/console";

    @Data
    public static class BasicAuthOptions {
        
        private boolean disabled = true;
    }

    @Data
    public static class TwoFactorAuthOptions {

        
        private boolean disabled;
    }

    @Data
    public static class TotpOptions {

        private String key;

        private String keystorePassword;
    }

    @Data
    public static class CorsOptions {

        private boolean disabled;

        private final List<CorsConfig> configs = new ArrayList<>();
    }

    @Data
    public static class CorsConfig {

        private String pathPattern;

        private CorsEndpointProperties config;
    }

    @Data
    public static class FrameOptions {

        private boolean disabled;

        private Mode mode = Mode.SAMEORIGIN;
    }

    @Data
    public static class ReferrerOptions {

        private ReferrerPolicy policy = STRICT_ORIGIN_WHEN_CROSS_ORIGIN;
    }

    @Data
    public static class RememberMeOptions {
        private Duration tokenValidity = Duration.ofDays(14);

        

        private Duration rotationCooldown = Duration.ofMinutes(5);
    }

    @Data
    public static class PasswordResetMethod {

        private String name;

        private URI href;

        private URI icon;
    }

    @Data
    public static class PasswordResetOptions {

        /** How long an emailed verification code remains valid. */
        private Duration codeTtl = Duration.ofMinutes(10);

        /** Maximum number of code attempts before the code is invalidated. */
        private int maxAttempts = 5;

        /** Minimum interval between two send-code requests for the same email. */
        private Duration resendCooldown = Duration.ofSeconds(60);

        /** From address used for password reset emails. */
        private String from = "no-reply@pudans.local";

        /**
         * Development fallback: write the generated code to the application log
         * when no JavaMailSender is configured. Disable this outside local use.
         */
        private boolean logCodes = true;
    }
}
