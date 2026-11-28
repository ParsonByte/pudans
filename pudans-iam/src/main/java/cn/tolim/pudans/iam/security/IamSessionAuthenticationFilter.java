package cn.tolim.pudans.iam.security;

import cn.tolim.pudans.iam.communal.session.LoginSessionEntity;
import cn.tolim.pudans.iam.core.auth.IamAuthTypes;
import cn.tolim.pudans.iam.core.auth.session.GlobalSessionService;
import cn.tolim.pudans.iam.core.auth.user.ApplicationAccessService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

public class IamSessionAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(IamSessionAuthenticationFilter.class);

    private final GlobalSessionService globalSessionService;
    private final UserAccountService userAccountService;
    private final ApplicationAccessService applicationAccessService;
    private final IamSessionCookieService cookieService;

    public IamSessionAuthenticationFilter(GlobalSessionService globalSessionService,
                                          UserAccountService userAccountService,
                                          ApplicationAccessService applicationAccessService,
                                          IamSessionCookieService cookieService) {
        this.globalSessionService = globalSessionService;
        this.userAccountService = userAccountService;
        this.applicationAccessService = applicationAccessService;
        this.cookieService = cookieService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isAuthenticated()) {
            String sid = cookieService.resolveSid(request);
            if (sid != null) {
                Optional<LoginSessionEntity> session = globalSessionService.findActive(sid);
                session.ifPresent(value -> authenticate(request, value));
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, LoginSessionEntity session) {
        try {
            UserDetails userDetails = userAccountService.loadUserDetails(session);
            userDetails.setApplications(applicationAccessService.listApplicationPrincipals(session));
            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken
                    .authenticated(userDetails, null, userDetails.getAuthorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            request.setAttribute(IamAuthTypes.SESSION_ATTRIBUTE, session);
        } catch (RuntimeException exception) {
            LOGGER.debug("Resolve global login session [{}] principal failed: {}", session.getSid(),
                    exception.getMessage());
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}