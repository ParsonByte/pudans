package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import com.google.common.net.HttpHeaders;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Component
@Order(0)
public class CorsConfigurer implements SecurityConfigurer {

    private final SecurityProperties.CorsOptions corsOptions;

    public CorsConfigurer(PudansProperties pudansProperties) {
        corsOptions = pudansProperties.getSecurity().getCorsOptions();
    }

    @Override
    public void configure(ServerHttpSecurity http) {
        http.cors(spec -> {
            if (corsOptions.isDisabled()) {
                spec.disable();
                return;
            }
            spec.configurationSource(apiCorsConfigSource());
        });
    }

    CorsConfigurationSource apiCorsConfigSource() {
        var source = new UrlBasedCorsConfigurationSource();

        this.corsOptions
                .getConfigs()
                .forEach(corsConfig -> source.registerCorsConfiguration(
                        corsConfig.getPathPattern(), corsConfig.getConfig().toCorsConfiguration()));


        var configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                "X-XSRF-TOKEN",
                HttpHeaders.COOKIE));
        // 默认跨域不携带凭据，Cookie 跨域场景请通过显式配置开启。
        configuration.setAllowCredentials(false);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));
        source.registerCorsConfiguration(ApiGroups.API_ROOT + "/**", configuration);
        return source;
    }
}
