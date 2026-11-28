package cn.tolim.pudans.repository.security;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

// Authenticates service calls that carry the internal token injected by the gateway
public class InternalTokenAuthenticationFilter extends OncePerRequestFilter {

    // Header shared by the gateway and every internal service
    public static final String TOKEN_HEADER = "X-Pudans-Internal-Token";

    // Probes and error dispatch stay reachable without the internal token
    public static final String[] PUBLIC_PATHS = {"/actuator/health", "/actuator/health/**",
            "/actuator/info", "/actuator/metrics", "/actuator/metrics/**", "/actuator/prometheus",
            "/actuator/prometheus/**", "/error"};

    private static final String PRINCIPAL = "pudans-internal";
    private static final String AUTHORITY = "ROLE_INTERNAL";

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public InternalTokenAuthenticationFilter(String expectedToken, ObjectMapper objectMapper) {
        if (expectedToken == null || expectedToken.isBlank()) {
            throw new IllegalArgumentException("pudans.internal.token must not be blank");
        }
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    public static boolean isPublicPath(String path) {
        for (String publicPath : PUBLIC_PATHS) {
            if (path.equals(publicPath) || path.startsWith(publicPath + "/")) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return isPublicPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String providedToken = request.getHeader(TOKEN_HEADER);
        if (providedToken != null && !providedToken.isBlank()
                && MessageDigest.isEqual(expectedToken,
                providedToken.getBytes(StandardCharsets.UTF_8))) {
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(PRINCIPAL, null,
                            List.of(new SimpleGrantedAuthority(AUTHORITY)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
            return;
        }
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(CommonErrorCode.UNAUTHORIZED.code(),
                        CommonErrorCode.UNAUTHORIZED.message()));
    }

}