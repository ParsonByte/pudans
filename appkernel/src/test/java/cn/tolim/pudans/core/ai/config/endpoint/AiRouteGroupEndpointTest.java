package cn.tolim.pudans.core.ai.config.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiRouteGroup;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiRouteGroupEndpointTest {

    @Mock
    ReactiveExtensionClient client;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        var endpoint = new AiRouteGroupEndpoint(client);
        webClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void arrangesRouteGroupsByTheGivenOrder() {
        var first = group("group-a");
        var second = group("group-b");
        when(client.get(AiRouteGroup.class, "group-b")).thenReturn(Mono.just(second));
        when(client.get(AiRouteGroup.class, "group-a")).thenReturn(Mono.just(first));
        when(client.update(any(AiRouteGroup.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webClient.post()
                .uri("/airoutegroups/-/arrange")
                .bodyValue(new AiRouteGroupEndpoint.ArrangeRequest(List.of("group-b", "group-a")))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].metadata.name").isEqualTo("group-b")
                .jsonPath("$[0].spec.order").isEqualTo(0)
                .jsonPath("$[1].metadata.name").isEqualTo("group-a")
                .jsonPath("$[1].spec.order").isEqualTo(1);

        verify(client, times(2)).update(any(AiRouteGroup.class));
    }

    @Test
    void rejectsDuplicateNamesWhenArranging() {
        webClient.post()
                .uri("/airoutegroups/-/arrange")
                .bodyValue(new AiRouteGroupEndpoint.ArrangeRequest(List.of("group-a", "group-a")))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void copiesARouteGroupUnderANewName() {
        var source = group("group-a");
        source.getSpec().setDisplayName("路由组 A");
        source.getSpec().setFallback(List.of("fast-group"));
        source.getSpec().setAccounts(List.of("account-a"));
        source.getSpec().setDefault(true);
        source.getSpec().setOrder(2);
        when(client.get(AiRouteGroup.class, "group-a")).thenReturn(Mono.just(source));
        when(client.get(AiRouteGroup.class, "group-c")).thenReturn(Mono.empty());
        when(client.create(any(AiRouteGroup.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webClient.post()
                .uri("/airoutegroups/group-a/actions/copy")
                .bodyValue(new AiRouteGroupEndpoint.CopyRequest("group-c", "副本组"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.metadata.name").isEqualTo("group-c")
                .jsonPath("$.spec.displayName").isEqualTo("副本组")
                .jsonPath("$.spec.isDefault").isEqualTo(false)
                .jsonPath("$.spec.order").isEqualTo(0)
                .jsonPath("$.spec.fallback[0]").isEqualTo("fast-group")
                .jsonPath("$.spec.accounts[0]").isEqualTo("account-a");

        var captor = ArgumentCaptor.forClass(AiRouteGroup.class);
        verify(client).create(captor.capture());
        assertThat(captor.getValue().getSpec()).isNotSameAs(source.getSpec());
    }

    @Test
    void rejectsAnExistingCopyTargetName() {
        when(client.get(AiRouteGroup.class, "group-a")).thenReturn(Mono.just(group("group-a")));
        when(client.get(AiRouteGroup.class, "group-c")).thenReturn(Mono.just(group("group-c")));

        webClient.post()
                .uri("/airoutegroups/group-a/actions/copy")
                .bodyValue(new AiRouteGroupEndpoint.CopyRequest("group-c", null))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    private static AiRouteGroup group(String name) {
        var group = new AiRouteGroup();
        var metadata = new Metadata();
        metadata.setName(name);
        group.setMetadata(metadata);
        group.getSpec().setDisplayName(name);
        return group;
    }
}