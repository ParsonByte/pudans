package cn.tolim.pudans.iam.infra.security.util;

import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;
import java.util.Optional;

public final class SecurityUtils {

    public static final String ANONYMOUS_USER = "anonymousUser";

    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    private SecurityUtils() {
    }

    public static boolean isCurrentUserInRole(String authority) {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(authentication -> authentication.getAuthorities().stream()
                        .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(authority)))
                .orElse(false);
    }

    public static String getCurrentUserName() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(SecurityUtils::resolveUsername).orElse(ANONYMOUS_USER);
    }

    public static boolean isPrincipalAuthenticated(Authentication authentication) {
        return authentication != null
                && !AnonymousAuthenticationToken.class.isAssignableFrom(authentication.getClass())
                && authentication.isAuthenticated();
    }

    public static String getPrincipal(AbstractAuthenticationFailureEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return principal instanceof String value ? value : null;
    }

    public static UserDetails getCurrentUser() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(authentication -> authentication.getPrincipal() instanceof UserDetails userDetails
                        ? userDetails
                        : null)
                .orElse(null);
    }

    public static String getCurrentUserId() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(authentication -> {
                    Object principal = authentication.getPrincipal();
                    if (principal instanceof UserDetails userDetails) {
                        return userDetails.getId();
                    }
                    return principal instanceof String value ? value : null;
                }).orElse(ANONYMOUS_USER);
    }

    public static UserType getCurrentUserType() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(authentication -> authentication.getPrincipal() instanceof UserDetails userDetails
                        ? userDetails.getUserType()
                        : null)
                .orElse(null);
    }

    public static SecurityContext getSecurityContext() {
        return SecurityContextHolder.getContext();
    }

    public static boolean isAuthenticated() {
        Authentication authentication = getSecurityContext().getAuthentication();
        return !Objects.isNull(authentication) && !TRUST_RESOLVER.isAnonymous(authentication)
                && authentication.isAuthenticated();
    }

    private static String resolveUsername(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return principal instanceof String value ? value : null;
    }
}
