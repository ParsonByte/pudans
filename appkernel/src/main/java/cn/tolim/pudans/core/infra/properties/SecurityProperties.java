package cn.tolim.pudans.core.infra.properties;

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

    // 交互式登录成功后的跳转地址。
    private String postLoginUrl = "/";

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

        // 邮件验证码的有效期。
        private Duration codeTtl = Duration.ofMinutes(10);

        // 验证码失效前的最大尝试次数。
        private int maxAttempts = 5;

        // 同一邮箱两次发送验证码之间的最小间隔。
        private Duration resendCooldown = Duration.ofSeconds(60);

        // 密码重置邮件的发件地址。
        private String from = "no-reply@pudans.local";

        // 开发回退：未配置 JavaMailSender 时将验证码写入应用日志；默认关闭，仅在本地开发时显式启用。
        private boolean logCodes = false;
    }
}
