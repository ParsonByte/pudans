package cn.tolim.pudans.iam.security;

import jakarta.servlet.http.HttpServletRequest;

public final class IamRequestUtils {

    private IamRequestUtils() {
    }

    // Resolves the client IP address, honoring reverse proxy headers
    // Parameter request: HttpServletRequest
    // Returns: String
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    // Resolves the user agent, truncating to the persisted column length
    // Parameter request: HttpServletRequest
    // Returns: String
    public static String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= 500 ? userAgent : userAgent.substring(0, 500);
    }
}