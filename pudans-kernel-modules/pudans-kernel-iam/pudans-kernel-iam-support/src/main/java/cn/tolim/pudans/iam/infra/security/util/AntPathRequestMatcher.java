package cn.tolim.pudans.iam.infra.security.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;

import java.util.Objects;

// Compatibility replacement for the matcher removed from Spring Security 7.
public final class AntPathRequestMatcher implements RequestMatcher {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final String pattern;

    private final String httpMethod;

    public AntPathRequestMatcher(String pattern) {
        this(pattern, null);
    }

    public AntPathRequestMatcher(String pattern, String httpMethod) {
        Assert.hasText(pattern, "匹配模式不能为空");
        Assert.isTrue(pattern.startsWith("/") || pattern.startsWith("*"),
                "匹配模式必须以 / 或 * 开头");
        this.pattern = pattern;
        this.httpMethod = httpMethod;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        if (this.httpMethod != null
                && !this.httpMethod.equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        String requestPath;
        if (servletPath == null) {
            requestPath = request.getRequestURI();
        } else {
            requestPath = pathInfo == null ? servletPath : servletPath + pathInfo;
        }
        return PATH_MATCHER.match(this.pattern, requestPath);
    }

    public String getPattern() {
        return this.pattern;
    }

    public String getHttpMethod() {
        return this.httpMethod;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AntPathRequestMatcher that)) {
            return false;
        }
        return Objects.equals(this.pattern, that.pattern)
                && Objects.equals(this.httpMethod, that.httpMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.pattern, this.httpMethod);
    }

    @Override
    public String toString() {
        return this.httpMethod == null ? this.pattern
                : this.httpMethod + " " + this.pattern;
    }
}
