package cn.tolim.pudans.iam.core.auth.ticket;

import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.repository.app.AppJwtConfigRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppOidcConfigRepository;
import cn.tolim.pudans.iam.core.auth.exception.InvalidAuthRequestException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class TicketTargetValidator {

    private static final int MAX_TARGET_LENGTH = 1000;

    private final AppJwtConfigRepository appJwtConfigRepository;
    private final AppOidcConfigRepository appOidcConfigRepository;

    public TicketTargetValidator(AppJwtConfigRepository appJwtConfigRepository,
                                 AppOidcConfigRepository appOidcConfigRepository) {
        this.appJwtConfigRepository = appJwtConfigRepository;
        this.appOidcConfigRepository = appOidcConfigRepository;
    }

    // Validates that the target URL belongs to a registered address of the application
    // Parameter app: AppEntity
    // Parameter targetUrl: String
    // Returns: String the normalized target or null when absent
    public String validate(AppEntity app, String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            return null;
        }
        String trimmed = targetUrl.trim();
        if (trimmed.length() > MAX_TARGET_LENGTH) {
            throw new InvalidAuthRequestException("target_invalid", "Target url is too long");
        }
        URI target = parse(trimmed);
        if (target == null) {
            throw new InvalidAuthRequestException("target_invalid", "Target url is not a valid absolute url");
        }
        Set<String> allowedOrigins = registeredOrigins(app);
        if (allowedOrigins.isEmpty()) {
            throw new InvalidAuthRequestException("target_not_allowed",
                    "The application has no registered url to validate the target");
        }
        if (!allowedOrigins.contains(origin(target))) {
            throw new InvalidAuthRequestException("target_not_allowed",
                    "Target url does not belong to the application");
        }
        return trimmed;
    }

    private Set<String> registeredOrigins(AppEntity app) {
        Set<String> origins = new LinkedHashSet<>();
        addOrigin(origins, app == null ? null : app.getInitLoginUrl());
        if (app == null || app.getId() == null) {
            return origins;
        }
        appJwtConfigRepository.findByAppId(app.getId())
                .ifPresent(config -> addOrigin(origins, config.getRedirectUrl()));
        appOidcConfigRepository.findByAppId(app.getId())
                .ifPresent(config -> {
                    if (config.getRedirectUris() != null) {
                        config.getRedirectUris().forEach(uri -> addOrigin(origins, uri));
                    }
                });
        return origins;
    }

    private void addOrigin(Set<String> origins, String url) {
        URI uri = parse(url);
        if (uri != null) {
            origins.add(origin(uri));
        }
    }

    private URI parse(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            return uri;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private String origin(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equals(scheme) ? 443 : 80;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return scheme + "://" + host + ":" + port;
    }
}