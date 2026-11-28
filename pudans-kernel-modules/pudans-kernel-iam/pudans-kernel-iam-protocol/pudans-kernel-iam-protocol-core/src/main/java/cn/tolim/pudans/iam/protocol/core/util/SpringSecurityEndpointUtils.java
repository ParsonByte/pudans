package cn.tolim.pudans.iam.protocol.core.util;

import org.springframework.security.web.SecurityFilterChain;

import java.util.Optional;


public final class SpringSecurityEndpointUtils<T> {

    // The OAuth 2 endpoint filter.
    private final T oauth2EndpointFilter;

    // Instantiates a new Spring doc security o auth 2 endpoint utils.
    // Parameter oauth2EndpointFilter the oauth 2 endpoint filter
    public SpringSecurityEndpointUtils(T oauth2EndpointFilter) {
        this.oauth2EndpointFilter = oauth2EndpointFilter;
    }

    // Find endpoint object.
    // Parameter filterChain the filter chain
    // Returns the object
    public Object findEndpoint(SecurityFilterChain filterChain) {
        Optional<?> oAuth2EndpointFilterOptional = filterChain.getFilters().stream()
                .filter(((Class<?>) oauth2EndpointFilter)::isInstance)
                .map(((Class<?>) oauth2EndpointFilter)::cast).findAny();
        return oAuth2EndpointFilterOptional.orElse(null);
    }
}
