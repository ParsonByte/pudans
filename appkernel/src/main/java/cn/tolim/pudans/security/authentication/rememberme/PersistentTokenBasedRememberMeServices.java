package cn.tolim.pudans.security.authentication.rememberme;

import cn.tolim.pudans.contract.core.extension.RememberMeToken;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.security.device.DeviceService;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.security.authentication.LoginParameterRequestCache;
import cn.tolim.pudans.security.authentication.SecurityConstant;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


@Slf4j
@Setter
@Component
public class PersistentTokenBasedRememberMeServices extends TokenBasedRememberMeServices implements RememberMeServices {

    public static final String REMEMBER_ME_SERIES_REQUEST_NAME = "remember-me-series";

    public static final int DEFAULT_SERIES_LENGTH = 16;

    public static final int DEFAULT_TOKEN_LENGTH = 16;

    private final SecureRandom random;

    private final int seriesLength = DEFAULT_SERIES_LENGTH;

    private final int tokenLength = DEFAULT_TOKEN_LENGTH;

    private final PersistentRememberMeTokenRepository tokenRepository;

    private final DeviceService deviceService;

    private final Duration rotationCooldown;

    private Clock clock = Clock.systemUTC();

    public PersistentTokenBasedRememberMeServices(
            CookieSignatureKeyResolver cookieSignatureKeyResolver,
            ReactiveUserDetailsService userDetailsService,
            RememberMeCookieResolver rememberMeCookieResolver,
            PersistentRememberMeTokenRepository tokenRepository,
            LoginParameterRequestCache parameterRequestCache,
            DeviceService deviceService,
            PudansProperties pudansProperties) {
        super(cookieSignatureKeyResolver, rememberMeCookieResolver, userDetailsService, parameterRequestCache);
        this.random = new SecureRandom();
        this.tokenRepository = tokenRepository;
        this.deviceService = deviceService;
        this.rotationCooldown = pudansProperties.getSecurity().getRememberMe().getRotationCooldown();
        setParameterName(SecurityConstant.REMEMBER_ME_PARAMETER_NAME);
    }

    @Override
    protected Mono<UserDetails> processAutoLoginCookie(String[] cookieTokens, ServerWebExchange exchange) {
        if (cookieTokens.length != 2) {
            return Mono.error(new InvalidCookieException("Cookie token did not contain %d tokens, but contained '%s'"
                    .formatted(2, Arrays.asList(cookieTokens))));
        }
        var presentedSeries = cookieTokens[0];
        var presentedToken = cookieTokens[1];
        log.debug("Processing remember-me auto-login for series '{}'", presentedSeries);
        return this.tokenRepository
                .getTokenForSeries(presentedSeries)
                .switchIfEmpty(Mono.error(() -> {
                    log.debug("No remember-me token found for series '{}'", presentedSeries);
                    return new RememberMeAuthenticationException(
                            "No persistent token found for series id: " + presentedSeries);
                }))
                .doOnNext(token -> log.debug(
                        "Found remember-me token for user '{}', series '{}', lastUsed={}, tokenMatch={}",
                        token.getSpec().getUsername(),
                        token.getSpec().getSeries(),
                        token.getSpec().getLastUsed(),
                        Objects.equals(token.getSpec().getTokenValue(), presentedToken)))
                .delayUntil(token -> validateDevice(exchange, token))
                .delayUntil(token -> {
                    if (!Objects.equals(token.getSpec().getTokenValue(), presentedToken)) {
                        if (isTokenStolen(token, presentedToken)) {
                            log.warn(
                                    "Cookie theft detected for user '{}', series '{}': "
                                            + "presentedToken does not match stored token "
                                            + "and is outside cooldown or does not match previous token. "
                                            + "Removing all tokens for this user.",
                                    token.getSpec().getUsername(),
                                    token.getSpec().getSeries());
                            return this.tokenRepository
                                    .removeUserTokens(token.getSpec().getUsername())
                                    .then(Mono.error(() -> new CookieTheftException("""
                                            Invalid remember-me token (Series/token) mismatch. \
                                            Implies previous cookie theft attack.""")));
                        }
                        log.debug(
                                "Previous remember-me token accepted for user '{}', series '{}'",
                                token.getSpec().getUsername(),
                                token.getSpec().getSeries());
                    }
                    if (isTokenExpired(token)) {
                        log.warn(
                                "Remember-me token expired for user '{}', series '{}', lastUsed={}, removing all user"
                                        + " tokens",
                                token.getSpec().getUsername(),
                                token.getSpec().getSeries(),
                                token.getSpec().getLastUsed());
                        return this.tokenRepository
                                .removeUserTokens(token.getSpec().getUsername())
                                .then(Mono.error(new InvalidCookieException("Remember-me login has expired")));
                    }
                    return Mono.empty();
                })
                .flatMap(token -> {
                    if (!Objects.equals(token.getSpec().getTokenValue(), presentedToken)) {
                        return Mono.just(token);
                    }
                    if (isRecentlyRotated(token)) {
                        log.debug(
                                "Skipping remember-me token rotation for user '{}', series '{}': "
                                        + "last rotated {} ago, cooldown is {}",
                                token.getSpec().getUsername(),
                                token.getSpec().getSeries(),
                                Duration.between(token.getSpec().getLastUsed(), clock.instant()),
                                rotationCooldown);
                        return Mono.just(token);
                    }
                    log.debug(
                            "Rotating remember-me token for user '{}', series '{}'",
                            token.getSpec().getUsername(),
                            token.getSpec().getSeries());
                    token.getSpec().setPreviousTokenValue(presentedToken);
                    token.getSpec().setTokenValue(generateTokenData());
                    token.getSpec().setLastUsed(clock.instant());
                    return tokenRepository
                            .updateToken(token)
                            .doOnNext(updated -> log.debug(
                                    "Remember-me token rotated successfully for user '{}', series '{}'",
                                    updated.getSpec().getUsername(),
                                    updated.getSpec().getSeries()))
                            .onErrorResume(OptimisticLockingFailureException.class, e -> {
                                log.warn(
                                        "Optimistic locking failure during token rotation "
                                                + "for user '{}', series '{}', "
                                                + "falling back to fresh token",
                                        token.getSpec().getUsername(),
                                        token.getSpec().getSeries());
                                return tokenRepository
                                        .getTokenForSeries(presentedSeries)
                                        .defaultIfEmpty(token);
                            });
                })
                .doOnNext(token -> addCookie(token, exchange))
                .flatMap(t -> getUserDetailsService().findByUsername(t.getSpec().getUsername()));
    }

    private Mono<Void> validateDevice(ServerWebExchange exchange, RememberMeToken token) {
        return deviceService
                .resolveCurrentDevice(exchange)
                .switchIfEmpty(Mono.error(() -> {
                    log.debug(
                            "Remember-me device validation failed for user '{}', series '{}': "
                                    + "no device cookie found",
                            token.getSpec().getUsername(),
                            token.getSpec().getSeries());
                    return new RememberMeAuthenticationException(
                            "Unable to determine device for remember-me authentication");
                }))
                .filter(d -> Objects.equals(
                        d.getSpec().getRememberMeSeriesId(), token.getSpec().getSeries()))
                .switchIfEmpty(Mono.error(() -> {
                    log.warn(
                            "Remember-me device validation failed for user '{}', series '{}': "
                                    + "device series ID does not match token series",
                            token.getSpec().getUsername(),
                            token.getSpec().getSeries());
                    return new RememberMeAuthenticationException(
                            "Remember-me series ID does not match current device's series ID");
                }))
                .then();
    }

    private boolean isTokenStolen(RememberMeToken token, String presentedToken) {


        if (Objects.equals(presentedToken, token.getSpec().getPreviousTokenValue())) {
            var lastUsed = Optional.ofNullable(token.getSpec().getLastUsed())
                    .orElseGet(() -> token.getMetadata().getCreationTimestamp());
            return clock.instant().isAfter(lastUsed.plus(rotationCooldown));
        }


        return true;
    }

    private boolean isTokenExpired(RememberMeToken token) {
        var lastUsed = Optional.ofNullable(token.getSpec().getLastUsed())
                .orElseGet(() -> token.getMetadata().getCreationTimestamp());
        var now = clock.instant();
        return now.isAfter(lastUsed.plus(rememberMeCookieResolver.getCookieMaxAge()));
    }


    private boolean isRecentlyRotated(RememberMeToken token) {
        var lastUsed = Optional.ofNullable(token.getSpec().getLastUsed())
                .orElseGet(() -> token.getMetadata().getCreationTimestamp());
        return clock.instant().isBefore(lastUsed.plus(rotationCooldown));
    }


    @Override
    protected Mono<Void> onLoginSuccess(ServerWebExchange exchange, Authentication successfulAuthentication) {
        var username = successfulAuthentication.getName();
        log.debug("Creating new remember-me persistent login for user '{}'", username);
        var t = new RememberMeToken();
        t.setMetadata(new Metadata());
        t.setSpec(new RememberMeToken.Spec());
        var seriesId = generateSeriesData();
        var tokenValue = generateTokenData();
        t.getMetadata().setGenerateName(username + '-');
        t.getSpec().setLastUsed(clock.instant());
        t.getSpec().setSeries(seriesId);
        t.getSpec().setTokenValue(tokenValue);
        t.getSpec().setUsername(username);
        return this.tokenRepository
                .createNewToken(t)
                .doOnNext(created -> {
                    log.debug(
                            "Remember-me token created for user '{}', series '{}'",
                            username,
                            created.getSpec().getSeries());
                    addCookie(created, exchange);
                })
                .doOnError(e -> log.error(
                        "Remember-me token could not be created for user '{}', series '{}'", username, seriesId, e))
                .onErrorComplete()
                .then();
    }

    @Override
    protected Mono<Void> onLogout(WebFilterExchange exchange, Authentication authentication) {
        if (authentication != null) {
            return this.tokenRepository.removeUserTokens(authentication.getName());
        }
        return Mono.empty();
    }

    private void addCookie(RememberMeToken token, ServerWebExchange exchange) {
        var spec = token.getSpec();
        setCookie(new String[] {spec.getSeries(), spec.getTokenValue()}, exchange);
        exchange.getAttributes().put(REMEMBER_ME_SERIES_REQUEST_NAME, spec.getSeries());
    }

    protected String generateSeriesData() {
        byte[] newSeries = new byte[this.seriesLength];
        this.random.nextBytes(newSeries);
        return new String(Base64.getEncoder().encode(newSeries));
    }

    protected String generateTokenData() {
        byte[] newToken = new byte[this.tokenLength];
        this.random.nextBytes(newToken);
        return new String(Base64.getEncoder().encode(newToken));
    }
}
