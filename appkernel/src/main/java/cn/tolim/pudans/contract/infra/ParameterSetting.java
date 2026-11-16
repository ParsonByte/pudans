package cn.tolim.pudans.contract.infra;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.convert.ApplicationConversionService;


public class ParameterSetting {
    public static final String PARAMETER_SETTINGS_DEFAULT = "parameter-settings-default";
    public static final String PARAMETER_SETTINGS = "parameter-settings";
    public static final String LEGACY_PARAMETER_SETTINGS_DEFAULT = "system-default";
    public static final String LEGACY_PARAMETER_SETTINGS = "system";
    public static final String MIGRATION_ANNOTATION = "pudans.tolim.cn/migrated-from";

    public static <T> @Nullable T get(Map<String, String> data, String key, Class<T> type) {
        var valueString = data.get(key);
        if (valueString == null) {
            return null;
        }
        var conversionService = ApplicationConversionService.getSharedInstance();
        if (conversionService.canConvert(String.class, type)) {
            return conversionService.convert(valueString, type);
        }
        return JsonUtils.jsonToObject(valueString, type);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "ParameterSettingBasic")
    public static class Basic {
        public static final String GROUP = "basic";
        String title;
        String subtitle;
        String logo;
        String favicon;
        String language;
        String externalUrl;

        @JsonIgnore
        public Optional<Locale> useSystemLocale() {
            return Optional.ofNullable(language).filter(StringUtils::isNotBlank).map(Locale::forLanguageTag);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "ParameterSettingUser")
    public static class User {
        public static final String GROUP = "user";

        @Schema(deprecated = true, description = "Deprecated: use session.defaultUserGroup instead.")
        String defaultRole;

        String protectedUsernames;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "ParameterSettingSite")
    public static class Site {
        public static final String GROUP = "site";

        @Schema(description = "Site name shown in the console and in shared pages.")
        @NotBlank
        @Size(max = 80)
        private String siteName;

        @Schema(description = "Site description, possibly shown in share page summaries.")
        @Size(max = 300)
        private String siteDescription;

        @Schema(description = "Public site URL used for callbacks from external services.")
        @Pattern(regexp = ValidationUtils.URL_REGEX)
        @Size(max = 2048)
        private String siteUrl;

        @Schema(description = "Custom HTML injected at the bottom of pages.")
        @Size(max = 20000)
        private String footerCode;

        @Schema(description = "Announcement shown to signed-in users; empty hides the announcement.")
        @Size(max = 2000)
        private String siteAnnouncement;

        @Schema(description = "Content hash of the announcement, used to decide whether it is shown again.",
                accessMode = Schema.AccessMode.READ_ONLY)
        @Size(max = 64)
        private String announcementVersion;

        @Schema(description = "Logo used in dark mode.")
        @Pattern(regexp = ValidationUtils.URL_REGEX)
        @Size(max = 2048)
        private String logoDark;

        @Schema(description = "Logo used in light mode.")
        @Pattern(regexp = ValidationUtils.URL_REGEX)
        @Size(max = 2048)
        private String logoLight;

        @Schema(description = "Browser tab icon; ico or svg is recommended.")
        @Pattern(regexp = ValidationUtils.URL_REGEX)
        @Size(max = 2048)
        private String favicon;

        @Schema(description = "192x192 png icon; the backend only provides a format hint.")
        @Pattern(regexp = ValidationUtils.URL_REGEX)
        @Size(max = 2048)
        private String iconMedium;

        @Schema(description = "512x512 png icon used by the iOS client when switching sites.")
        @Pattern(regexp = ValidationUtils.URL_REGEX)
        @Size(max = 2048)
        private String iconLarge;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "ParameterSettingSession")
    public static class Session {
        public static final String GROUP = "session";

        @Schema(description = "Allow signing in with bound hardware authenticators.")
        private Boolean passkeyLoginEnabled;

        @Schema(description = "Expose other users' email addresses to signed-in users.")
        private Boolean exposeEmailToSignedInUsers;

        @Schema(description = "Initial user group for newly provisioned users.")
        @Pattern(regexp = ValidationUtils.NAME_REGEX)
        @Size(max = 63)
        private String defaultUserGroup;

        @Schema(description = "Avatar storage path relative to the data directory.")
        @Pattern(regexp = ValidationUtils.RELATIVE_PATH_REGEX)
        @Size(max = 255)
        private String avatarStoragePath;

        @Schema(description = "Maximum avatar upload size in bytes.")
        @Min(1)
        @Max(52428800)
        private Integer avatarMaxSize;

        @Schema(description = "Avatar edge length in pixels after resizing.")
        @Min(16)
        @Max(2048)
        private Integer avatarDimension;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "ParameterSettingCaptcha")
    public static class Captcha {
        public static final String GROUP = "captcha";

        private Boolean loginCaptchaEnabled;

        private Boolean recoveryCaptchaEnabled;

        @Schema(description = "Captcha provider; image is the built-in provider and none disables verification.")
        @Pattern(regexp = "^[A-Za-z0-9_-]*$")
        @Size(max = 64)
        private String captchaProvider;

        @Schema(description = "Captcha generation mode: TEXT or MATH.")
        @Pattern(regexp = "^(|TEXT|MATH)$")
        private String captchaMode;

        @Schema(description = "Captcha character count for TEXT mode.")
        @Min(3)
        @Max(8)
        private Integer captchaLength;

        private Boolean captchaInterferenceText;

        private Boolean captchaInterferenceDots;

        private Boolean captchaUseHollowLine;

        private Boolean captchaUseNoise;

        private Boolean captchaUseInterferenceText;

        private Boolean captchaUseWaveLine;

        private Boolean captchaUseSineLine;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "ParameterSettingMail")
    public static class Mail {
        public static final String GROUP = "mail";

        @Size(max = 120)
        private String mailFromName;

        @Email
        @Size(max = 254)
        private String mailFromAddress;

        @Size(max = 255)
        private String smtpHost;

        @Min(1)
        @Max(65535)
        private Integer smtpPort;

        @Schema(writeOnly = true)
        @Size(max = 255)
        private String smtpUsername;

        @Schema(writeOnly = true)
        @Size(max = 512)
        private String smtpPassword;

        @Email
        @Size(max = 254)
        private String mailReplyTo;

        private Boolean smtpForceSsl;

        @Schema(description = "Seconds an established SMTP connection may be reused.")
        @Min(0)
        @Max(86400)
        private Integer smtpConnectionTtlSeconds;

        @Schema(description = "Optional templates keyed by mail type. Supported placeholders: "
                + "{{siteName}}, {{siteUrl}}, {{username}}, {{actionLink}}, {{code}}.")
        @Size(max = 50)
        private Map<String, String> mailTemplates;

        @Schema(description = "Declarative attachments keyed by mail template. Files are read from the classpath.")
        @Size(max = 50)
        private Map<String, List<MailTemplateAttachment>> mailTemplateAttachments;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "ParameterSettingMailTemplateAttachment")
    public static class MailTemplateAttachment {

        @Size(max = 255)
        private String fileName;

        @NotBlank
        @Size(max = 1024)
        private String classpath;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "ParameterSettingAudit")
    public static class Audit {
        public static final String GROUP = "audit";

        private AuditRecordRules auditRecordRules;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "ParameterSettingAuditRecordRules")
    public static class AuditRecordRules {
        @Schema(description = "WHITELIST records only matching entries; BLACKLIST skips matching entries.")
        private String mode;

        private List<String> operations;

        private List<String> resourceTypes;

        @JsonIgnore
        public boolean isWhitelist() {
            return "WHITELIST".equalsIgnoreCase(mode);
        }

        @JsonIgnore
        public boolean isBlacklist() {
            return "BLACKLIST".equalsIgnoreCase(mode);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "ParameterSettingServer")
    public static class Server {
        public static final String GROUP = "server";

        @Schema(description = "Temporary file directory relative to the data directory.")
        @Pattern(regexp = ValidationUtils.RELATIVE_PATH_REGEX)
        @Size(max = 255)
        private String tempPath;

        @Schema(description = "Unique site identifier; generated on first startup.",
                accessMode = Schema.AccessMode.READ_ONLY)
        @Size(max = 64)
        private String siteId;

        @Schema(writeOnly = true, description = "Master key. Changes take effect after a restart.")
        @Size(max = 512)
        private String masterKey;

        @Schema(writeOnly = true, description = "HashID salt. Changes take effect after a restart.")
        @Size(max = 512)
        private String hashIdSalt;

        @Schema(description = "Access token time-to-live in seconds.")
        @Min(60)
        @Max(31536000)
        private Integer accessTokenTtlSeconds;

        @Schema(description = "Refresh token time-to-live in seconds; read-only until a refresh token flow exists.")
        @Min(60)
        @Max(31536000)
        private Integer refreshTokenTtlSeconds;

        @Schema(description = "Six-field cron expression for garbage collection.")
        @Size(max = 64)
        private String gcCron;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "ParameterSettingSeo")
    public static class Seo {
        public static final String GROUP = "seo";
        Boolean blockSpiders;
        String keywords;
        String description;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "ParameterSettingAuthProvider")
    public static class AuthProvider {
        public static final String GROUP = "authProvider";

        private List<AuthProviderState> states;
    }

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(name = "ParameterSettingAuthProviderState")
    public static class AuthProviderState {
        private String name;
        private boolean enabled;
        private int priority;
    }
}
