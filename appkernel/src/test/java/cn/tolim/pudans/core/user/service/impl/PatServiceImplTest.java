package cn.tolim.pudans.core.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.security.PersonalAccessToken;
import cn.tolim.pudans.core.rbac.BuiltinEntities;
import cn.tolim.pudans.infra.exception.UnsatisfiedAttributeValueException;
import cn.tolim.pudans.infra.setting.MasterKeyService;
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
class PatServiceImplTest {

    @Mock
    RoleService roleService;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ReactiveUserDetailsService userDetailsService;

    @Mock
    MasterKeyService masterKeyService;

    private PatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PatServiceImpl(roleService, client, userDetailsService, masterKeyService);
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
}
