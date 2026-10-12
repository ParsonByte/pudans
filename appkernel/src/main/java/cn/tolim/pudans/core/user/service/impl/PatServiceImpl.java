package cn.tolim.pudans.core.user.service.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.security.PersonalAccessToken;
import cn.tolim.pudans.core.user.service.PatService;
import cn.tolim.pudans.infra.exception.NotFoundException;
import cn.tolim.pudans.infra.exception.UnsatisfiedAttributeValueException;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import com.google.common.hash.Hashing;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.CollectionUtils;
import org.springframework.util.IdGenerator;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;


@Service
class PatServiceImpl implements PatService {

    private final RoleService roleService;

    private final IdGenerator idGenerator;

    private final ReactiveExtensionClient client;

    private final AuthenticationTrustResolver authTrustResolver = new AuthenticationTrustResolverImpl();

    private final ReactiveUserDetailsService userDetailsService;

    private final SecureRandom secureRandom = new SecureRandom();

    private Clock clock;

    public PatServiceImpl(
            RoleService roleService, ReactiveExtensionClient client, ReactiveUserDetailsService userDetailsService) {
        this.roleService = roleService;
        this.client = client;
        this.userDetailsService = userDetailsService;
        this.clock = Clock.systemUTC();
        idGenerator = new AlternativeJdkIdGenerator();
    }

    static String hashSecret(String secret) {
        return Hashing.sha256().hashString(secret, UTF_8).toString();
    }

    static String buildToken(String patName, String secret) {
        var encoded = Base64.getUrlEncoder().withoutPadding().encodeToString((patName + ':' + secret).getBytes(UTF_8));
        return PersonalAccessToken.PAT_TOKEN_PREFIX + encoded;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Mono<PersonalAccessToken> create(PersonalAccessToken patRequest) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)

                .filter(authTrustResolver::isAuthenticated)
                .switchIfEmpty(Mono.error(
                        () -> new UnsatisfiedAttributeValueException("problemDetail.authentication.required")))
                .flatMap(auth -> create(patRequest, auth.getName(), auth.getAuthorities()));
    }

    @Override
    public Mono<PersonalAccessToken> create(PersonalAccessToken patRequest, String username) {
        return userDetailsService
                .findByUsername(username)
                .flatMap(userDetails -> create(patRequest, username, userDetails.getAuthorities()));
    }

    private Mono<PersonalAccessToken> create(
            PersonalAccessToken patRequest, String username, Collection<? extends GrantedAuthority> authorities) {
        var patSpec = patRequest.getSpec();

        var expiresAt = patSpec.getExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(clock.instant())) {
            return Mono.error(new UnsatisfiedAttributeValueException("problemDetail.pat.invalidExpiry"));
        }
        var roles = patSpec.getRoles();
        return hasSufficientRoles(authorities, roles)
                .filter(has -> has)
                .switchIfEmpty(
                        Mono.error(() -> new UnsatisfiedAttributeValueException("problemDetail.pat.insufficientRoles")))
                .map(has -> {
                    var pat = new PersonalAccessToken();
                    pat.setMetadata(new Metadata());
                    if (patRequest.getMetadata() != null) {
                        var metadata = patRequest.getMetadata();
                        if (metadata.getName() != null) {
                            pat.getMetadata().setName(metadata.getName());
                        }
                        if (metadata.getGenerateName() != null) {
                            pat.getMetadata().setGenerateName(metadata.getGenerateName());
                        }
                        if (metadata.getLabels() != null) {
                            pat.getMetadata().setLabels(new HashMap<>());
                            pat.getMetadata().getLabels().putAll(metadata.getLabels());
                        }
                        if (metadata.getAnnotations() != null) {
                            pat.getMetadata().setAnnotations(new HashMap<>());
                            pat.getMetadata().getAnnotations().putAll(metadata.getAnnotations());
                        }
                        if (metadata.getFinalizers() != null) {
                            pat.getMetadata().setFinalizers(new HashSet<>());
                            pat.getMetadata().getFinalizers().addAll(metadata.getFinalizers());
                        }
                    }
                    if (pat.getMetadata().getGenerateName() == null) {
                        pat.getMetadata().setGenerateName("pat-" + username + "-");
                    }
                    pat.getSpec().setUsername(username);
                    pat.getSpec().setName(patSpec.getName());
                    pat.getSpec().setDescription(patSpec.getDescription());
                    if (patSpec.getRoles() != null) {
                        pat.getSpec().setRoles(new ArrayList<>());
                        pat.getSpec().getRoles().addAll(patSpec.getRoles());
                    }
                    if (patSpec.getScopes() != null) {
                        pat.getSpec().setScopes(new ArrayList<>());
                        pat.getSpec().getScopes().addAll(patSpec.getScopes());
                    }
                    if (patSpec.getScopeMode() != null) {
                        pat.getSpec().setScopeMode(patSpec.getScopeMode());
                    }
                    pat.getSpec().setExpiresAt(patSpec.getExpiresAt());
                    pat.getSpec().setTokenId(idGenerator.generateId().toString());
                    return pat;
                })
                .flatMap(client::create);
    }

    @Override
    public Mono<PersonalAccessToken> revoke(String patName, String username) {
        return get(patName, username)
                .filter(pat -> !pat.getSpec().isRevoked())
                .switchIfEmpty(
                        Mono.error(() -> new UnsatisfiedAttributeValueException("problemDetail.pat.alreadyRevoked")))
                .doOnNext(pat -> {
                    pat.getSpec().setRevoked(true);
                    pat.getSpec().setRevokesAt(clock.instant());
                })
                .flatMap(client::update);
    }

    @Override
    public Mono<PersonalAccessToken> restore(String patName, String username) {
        return get(patName, username)
                .filter(pat -> pat.getSpec().isRevoked())
                .switchIfEmpty(Mono.error(() -> new UnsatisfiedAttributeValueException("problemDetail.pat.notRevoked")))
                .doOnNext(pat -> {
                    pat.getSpec().setRevoked(false);
                    pat.getSpec().setRevokesAt(null);
                })
                .flatMap(client::update);
    }

    @Override
    public Mono<PersonalAccessToken> delete(String patName, String username) {
        return get(patName, username).flatMap(client::delete);
    }

    @Override
    public Mono<PersonalAccessToken> get(String patName, String username) {
        return client.fetch(PersonalAccessToken.class, patName)
                .filter(pat -> Objects.equals(pat.getSpec().getUsername(), username))
                .switchIfEmpty(Mono.error(() -> new NotFoundException(
                        "problemDetail.pat.notFound", null, "The personal access token was not found or deleted.")));
    }

    @Override
    public Mono<String> generateToken(PersonalAccessToken pat) {


        return Mono.fromCallable(this::generateSecret)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(secret -> {

                    pat.getSpec().setTokenId(hashSecret(secret));
                    return client.update(pat)
                            .thenReturn(buildToken(pat.getMetadata().getName(), secret));
                });
    }

    private String generateSecret() {
        var bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Mono<Boolean> hasSufficientRoles(
            Collection<? extends GrantedAuthority> grantedAuthorities, List<String> requestRoles) {
        if (CollectionUtils.isEmpty(requestRoles)) {
            return Mono.just(true);
        }
        var grantedRoles = AuthorityUtils.authoritiesToRoles(grantedAuthorities);
        return roleService.contains(grantedRoles, requestRoles);
    }
}
