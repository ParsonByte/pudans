package cn.tolim.pudans.iam.security;

import cn.tolim.pudans.communal.core.security.JwtTokenService;
import cn.tolim.pudans.communal.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

// Keeps the gateway issued JWT working for machine and management callers,
// but never overrides an authenticated browser session.
public class IamJwtAuthenticationFilter extends JwtAuthenticationFilter {

    public IamJwtAuthenticationFilter(JwtTokenService jwtTokenService, ObjectMapper objectMapper) {
        super(jwtTokenService, objectMapper);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}