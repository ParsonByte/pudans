package cn.tolim.pudans.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.resource.User;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.security.PersonalAccessToken;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ExtensionPersonalTokensTest {

    @Mock
    Roles roles;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ReactiveUserDetailsService userDetailsService;

    @Mock
    MasterKeys masterKeys;

    private ExtensionPersonalTokens service;

    @BeforeEach
    void setUp() {
        service = new ExtensionPersonalTokens(roles, client, userDetailsService, masterKeys);
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
        when(userDetailsService.findByUsername("alice"))
                .thenReturn(Mono.just(user(BuiltinEntities.NORMAL_ROLE)));

        StepVerifier.create(service.create(request(true), "alice"))
                .expectError(UnsatisfiedAttributeValueException.class)
                .verify();
    }

    @Test
    void adminCanCreateASudoToken() {
        when(userDetailsService.findByUsername("alice"))
                .thenReturn(Mono.just(user(BuiltinEntities.SYSTEM_ADMIN_ROLE)));
        when(client.create(any(PersonalAccessToken.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.create(request(true), "alice"))
                .assertNext(pat -> assertThat(pat.getSpec().isSudo()).isTrue())
                .verifyComplete();
    }

    @Test
    void copiesTheGatewaySpecIntoTheStoredToken() {
        when(userDetailsService.findByUsername("alice"))
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

    @Test
    void rejectsMoreThanFiveGatewayLimits() {
        when(userDetailsService.findByUsername("alice"))
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
