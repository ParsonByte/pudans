package cn.tolim.pudans.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.security.contract.PersonalAccessToken;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.infra.exception.UnsatisfiedAttributeValueException;
import cn.tolim.pudans.infra.setting.MasterKeys;
import cn.tolim.pudans.security.authentication.PudansUserDetails;
import cn.tolim.pudans.security.authentication.login.PudansUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ExtensionPersonalTokensTest {

    @Mock
    Roles roles;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ReactiveUserDetailsService reactiveUserDetailsService;

    @Mock
    MasterKeys masterKeys;

    private ExtensionPersonalTokens service;

    @BeforeEach
    void setUp() {
        service = new ExtensionPersonalTokens(roles, client, reactiveUserDetailsService, masterKeys);
    }

    private static UserDetails user(String role) {
        var details = new PudansUserDetails(
                "alice", "encoded", List.of(new SimpleGrantedAuthority("ROLE_" + role)), false);
        return new PudansUser.Builder(details).build();
    }

    private static PersonalAccessToken request(boolean sudo) {
        var pat = new PersonalAccessToken();
        var metadata = new Metadata();
        metadata.setName("pat-1");
        pat.setMetadata(metadata);
        var spec = new PersonalAccessToken.Spec();
        spec.setName("pat");
        spec.setSudo(sudo);
        pat.setSpec(spec);
        return pat;
    }

    @Test
    void nonAdminCannotCreateASudoToken() {
        when(reactiveUserDetailsService.findByUsername("alice"))
                .thenReturn(Mono.just(user(BuiltinEntities.NORMAL_ROLE)));

        StepVerifier.create(service.create(request(true), "alice"))
                .expectError(UnsatisfiedAttributeValueException.class)
                .verify();
    }

    @Test
    void adminCanCreateASudoToken() {
        when(reactiveUserDetailsService.findByUsername("alice"))
                .thenReturn(Mono.just(user(BuiltinEntities.SYSTEM_ADMIN_ROLE)));
        when(client.create(any(PersonalAccessToken.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.create(request(true), "alice"))
                .assertNext(pat -> assertThat(pat.getSpec().isSudo()).isTrue())
                .verifyComplete();
    }

    @Test
    void copiesTheGatewaySpecIntoTheStoredToken() {
        when(reactiveUserDetailsService.findByUsername("alice"))
                .thenReturn(Mono.just(user(BuiltinEntities.SYSTEM_ADMIN_ROLE)));
        when(client.create(any(PersonalAccessToken.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var request = request(false);
        var gateway = new PersonalAccessToken.Gateway();
        gateway.setMaxConcurrency(3);
        gateway.setModels(List.of("model-a"));
        var limit = new PersonalAccessToken.Limit();
        limit.setPeriod(PersonalAccessToken.LimitPeriod.DAY);
        limit.setTokens(1000L);
        gateway.setLimits(List.of(limit));
        request.getSpec().setGateway(gateway);

        StepVerifier.create(service.create(request, "alice"))
                .assertNext(pat -> {
                    assertThat(pat.getSpec().getGateway()).isNotSameAs(gateway);
                    assertThat(pat.getSpec().getGateway().getMaxConcurrency()).isEqualTo(3);
                    assertThat(pat.getSpec().getGateway().getModels()).containsExactly("model-a");
                    assertThat(pat.getSpec().getGateway().getLimits()).hasSize(1);
                })
                .verifyComplete();
    }

    // sudo 会话只能临时代表目标用户，不能创建持久令牌。
    @Test
    void sudoSessionCannotCreateAPersonalToken() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "alice")
                .claim("sudo_user", "alice")
                .build();
        var authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + BuiltinEntities.SYSTEM_ADMIN_ROLE)),
                "alice");

        StepVerifier.create(service
                        .create(request(false))
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                .expectError(UnsatisfiedAttributeValueException.class)
                .verify();
    }

    // 撤销全部令牌时必须覆盖每个未撤销记录。
    @Test
    void revokeAllMarksEveryActiveTokenRevoked() {
        var pat = request(false);
        pat.getSpec().setUsername("alice");
        when(client.listAll(eq(PersonalAccessToken.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(pat));
        when(client.update(pat)).thenReturn(Mono.just(pat));

        StepVerifier.create(service.revokeAll("alice")).expectNext(1L).verifyComplete();

        assertThat(pat.getSpec().isRevoked()).isTrue();
        assertThat(pat.getSpec().getRevokesAt()).isNotNull();
    }

    // 带 scope 的令牌只能签发更窄的子令牌。
    @Test
    void scopedTokenCannotCreateWiderChildScopes() {
        var request = request(false);
        request.getSpec().setScopes(List.of("admin:write"));

        StepVerifier.create(service
                        .create(request)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                scopedAuthentication("users:read"))))
                .expectError(UnsatisfiedAttributeValueException.class)
                .verify();
    }

    // 带 scope 的令牌不能通过 RolesOnly 丢弃自身限制。
    @Test
    void scopedTokenCannotCreateRolesOnlyChild() {
        var request = request(false);
        request.getSpec().setScopes(List.of("users:read"));
        request.getSpec().setScopeMode(PersonalAccessToken.SCOPE_MODE_ROLES_ONLY);

        StepVerifier.create(service
                        .create(request)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                scopedAuthentication("users:read"))))
                .expectError(UnsatisfiedAttributeValueException.class)
                .verify();
    }

    // 子令牌 scope 是调用方 scope 的子集时允许创建。
    @Test
    void scopedTokenCanCreateNarrowerChildScopes() {
        var request = request(false);
        request.getSpec().setScopes(List.of("users:read"));
        when(client.create(any(PersonalAccessToken.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service
                        .create(request)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                scopedAuthentication("users:read", "users:write"))))
                .assertNext(pat -> assertThat(pat.getSpec().getScopes()).containsExactly("users:read"))
                .verifyComplete();
    }

    private static JwtAuthenticationToken scopedAuthentication(String... scopes) {
        var jwt = Jwt.withTokenValue("pat")
                .header("alg", "none")
                .claim("sub", "alice")
                .build();
        var authorities = java.util.Arrays.stream(scopes)
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, "alice");
    }

    @Test
    void rejectsMoreThanFiveGatewayLimits() {        when(reactiveUserDetailsService.findByUsername("alice"))
                .thenReturn(Mono.just(user(BuiltinEntities.SYSTEM_ADMIN_ROLE)));
        var request = request(false);
        var gateway = new PersonalAccessToken.Gateway();
        gateway.setLimits(List.of(
                new PersonalAccessToken.Limit(),
                new PersonalAccessToken.Limit(),
                new PersonalAccessToken.Limit(),
                new PersonalAccessToken.Limit(),
                new PersonalAccessToken.Limit(),
                new PersonalAccessToken.Limit()));
        request.getSpec().setGateway(gateway);

        StepVerifier.create(service.create(request, "alice"))
                .expectError(UnsatisfiedAttributeValueException.class)
                .verify();
    }
}
