package cn.tolim.pudans.security.authentication.token;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.core.rbac.UserRoles;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.security.contract.PersonalAccessToken;
import cn.tolim.pudans.infra.setting.MasterKeys;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PatAuthenticationManagerTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ReactiveAuthenticationManager delegate;

    @Mock
    Roles roles;

    @Mock
    UserRoles userRoles;

    @Mock
    MasterKeys masterKeys;

    private PatAuthenticationManager manager;

    @BeforeEach
    void setUp() {
        manager = new PatAuthenticationManager(client, delegate, roles, userRoles, masterKeys);
    }

    private static User user(String username) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(username);
        user.setMetadata(metadata);
        user.setSpec(new User.UserSpec());
        return user;
    }

    @Test
    void sudoUsesTheIntersectionOfTokenAndTargetRoles() {
        var pat = new PersonalAccessToken();
        var metadata = new Metadata();
        metadata.setName("pat-1");
        pat.setMetadata(metadata);
        var spec = new PersonalAccessToken.Spec();
        spec.setUsername("alice");
        spec.setRoles(List.of("normal-role"));
        spec.setSudo(true);
        spec.setTokenId("token-id");
        pat.setSpec(spec);

        when(masterKeys.matches(eq("token-id"), eq("secret"), any())).thenReturn(true);
        when(client.fetch(PersonalAccessToken.class, "pat-1")).thenReturn(Mono.just(pat));
        when(client.fetch(User.class, "alice")).thenReturn(Mono.just(user("alice")));
        when(client.fetch(User.class, "bob")).thenReturn(Mono.just(user("bob")));
        when(userRoles.list("alice")).thenReturn(Flux.just("normal-role"));
        when(userRoles.list("bob")).thenReturn(Flux.just("normal-role", "system-admin-role"));
        when(roles.contains(anyCollection(), anyList())).thenReturn(Mono.just(true));
        when(client.update(pat)).thenReturn(Mono.just(pat));

        var token = PersonalAccessToken.PAT_TOKEN_PREFIX + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("pat-1:secret".getBytes(UTF_8));

        StepVerifier.create(manager.authenticate(new SudoBearerTokenAuthenticationToken(token, "bob")))
                .assertNext(authentication -> {
                    assertThat(authentication.getName()).isEqualTo("bob");
                    var jwt = (JwtAuthenticationToken) authentication;
                    assertThat(jwt.getToken().getClaimAsString("pat_owner")).isEqualTo("alice");
                    assertThat(jwt.getToken().getClaimAsString("sudo_user")).isEqualTo("bob");
                    assertThat(jwt.getToken().getClaimAsString("tokenId")).isEqualTo("token-id");
                    assertThat(jwt.getAuthorities().stream()
                                    .map(GrantedAuthority::getAuthority)
                                    .toList())
                            .contains("ROLE_normal-role")
                            .doesNotContain("ROLE_system-admin-role");
                })
                .verifyComplete();
    }

    @Test
    void patRolesMayBeInheritedFromUserGroups() {
        var pat = new PersonalAccessToken();
        var metadata = new Metadata();
        metadata.setName("pat-2");
        pat.setMetadata(metadata);
        var spec = new PersonalAccessToken.Spec();
        spec.setUsername("carol");
        spec.setRoles(List.of("gateway-role"));
        spec.setTokenId("token-2");
        pat.setSpec(spec);

        when(masterKeys.matches(eq("token-2"), eq("secret"), any())).thenReturn(true);
        when(client.fetch(PersonalAccessToken.class, "pat-2")).thenReturn(Mono.just(pat));
        when(client.fetch(User.class, "carol")).thenReturn(Mono.just(user("carol")));
        when(userRoles.list("carol")).thenReturn(Flux.just("gateway-role"));
        when(roles.contains(anyCollection(), anyList()))
                .thenAnswer(invocation -> Mono.just(invocation.<java.util.Collection<String>>getArgument(0)
                        .contains("gateway-role")));
        when(client.update(pat)).thenReturn(Mono.just(pat));

        var token = PersonalAccessToken.PAT_TOKEN_PREFIX + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("pat-2:secret".getBytes(UTF_8));

        StepVerifier.create(manager.authenticate(new BearerTokenAuthenticationToken(token)))
                .assertNext(authentication -> assertThat(authentication.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .toList())
                        .contains("ROLE_gateway-role"))
                .verifyComplete();
    }
}
