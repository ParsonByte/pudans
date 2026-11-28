package cn.tolim.pudans.iam.security;

import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.IamAuthTypes;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class IamSessionCookieService {

    private final IamAuthProperties properties;

    public IamSessionCookieService(IamAuthProperties properties) {
        this.properties = properties;
    }

    // Resolves the global session id from the internal header or the browser cookie
    // Parameter request: HttpServletRequest
    // Returns: String
    public String resolveSid(HttpServletRequest request) {
        String header = request.getHeader(IamAuthTypes.SESSION_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String cookieName = properties.getCookie().getName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    // Writes the HttpOnly global login session cookie
    // Parameter response: HttpServletResponse
    // Parameter sid: String
    // Parameter expiresAt: LocalDateTime
    public void writeSessionCookie(HttpServletResponse response, String sid, LocalDateTime expiresAt) {
        Duration maxAge = expiresAt == null ? properties.getSession().getTtl()
                : Duration.between(LocalDateTime.now(), expiresAt);
        if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
            maxAge = properties.getSession().getTtl();
        }
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(sid, maxAge).toString());
    }

    // Clears the global login session cookie
    // Parameter response: HttpServletResponse
    public void clearSessionCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.getCookie().getName(), value)
                .httpOnly(true)
                .secure(Boolean.TRUE.equals(properties.getCookie().getSecure()))
                .sameSite(properties.getCookie().getSameSite())
                .path(properties.getCookie().getPath())
                .maxAge(maxAge)
                .build();
    }
}