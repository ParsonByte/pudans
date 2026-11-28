package cn.tolim.pudans.communal.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

// Logs request method, path, status and latency; query strings are never logged.
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final PudansLogProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RequestLoggingFilter(PudansLogProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (properties.getIgnoredPaths().isEmpty()) {
            return false;
        }
        String path = request.getRequestURI();
        return properties.getIgnoredPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
            if (elapsed.compareTo(properties.getSlowRequestThreshold()) >= 0) {
                LOGGER.warn("Slow request: method={}, path={}, status={}, elapsedMs={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), elapsed.toMillis());
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Request completed: method={}, path={}, status={}, elapsedMs={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), elapsed.toMillis());
            }
        }
    }
}