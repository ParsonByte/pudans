package cn.tolim.pudans.contract.infra;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
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


public class SystemSetting {
    public static final String SYSTEM_CONFIG_DEFAULT = "system-default";
    public static final String SYSTEM_CONFIG = "system";

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
    public static class User {
        public static final String GROUP = "user";
        String defaultRole;

        String protectedUsernames;
    }

    @Data
    public static class Captcha {
        public static final String GROUP = "captcha";
        Boolean enabled;

        String provider;
    }

    @Data
    public static class Mail {
        public static final String GROUP = "mail";
        String host;

        Integer port;

        String username;

        String password;

        String from;

        Boolean ssl;
    }

    @Data
    public static class Seo {
        public static final String GROUP = "seo";
        Boolean blockSpiders;
        String keywords;
        String description;
    }

    @Data
    public static class AuthProvider {
        public static final String GROUP = "authProvider";

        private List<AuthProviderState> states;
    }

    @Data
    @Accessors(chain = true)
    public static class AuthProviderState {
        private String name;
        private boolean enabled;
        private int priority;
    }
}
