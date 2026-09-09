package cn.tolim.pudans.security.authentication;

import static cn.tolim.pudans.security.authentication.PudansServerRequestCache.uriInApplication;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.security.web.server.ServerRedirectStrategy;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import reactor.core.publisher.Mono;


@Slf4j
public class PudansRedirectAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    private final ServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    private final URI location;

    public PudansRedirectAuthenticationSuccessHandler(String location) {
        this.location = URI.create(location);
    }

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        var request = webFilterExchange.getExchange().getRequest();
        var redirectUriQuery = request.getQueryParams().getFirst("redirect_uri");
        if (redirectUriQuery == null || redirectUriQuery.isBlank()) {
            return redirectStrategy.sendRedirect(webFilterExchange.getExchange(), location);
        }
        var redirectUri = uriInApplication(request, URI.create(redirectUriQuery));
        if (log.isDebugEnabled()) {
            log.debug("Redirecting to: {} after switching to {}", redirectUri, authentication.getName());
        }
        return redirectStrategy.sendRedirect(webFilterExchange.getExchange(), URI.create(redirectUri));
    }
}
