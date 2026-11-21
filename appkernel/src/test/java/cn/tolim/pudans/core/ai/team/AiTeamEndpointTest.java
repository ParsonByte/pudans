package cn.tolim.pudans.core.ai.team;

import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiTeam;
import cn.tolim.pudans.contract.extension.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiTeamEndpointTest {

    @Mock
    Teams teams;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToRouterFunction(new AiTeamEndpoint(teams).endpoint())
                .build();
    }

    @Test
    void addsAMember() {
        var team = team("team-a");
        when(teams.addMember("team-a", "bob", AiTeam.TeamRole.MEMBER)).thenReturn(Mono.just(team));

        client.post()
                .uri("/aiteams/team-a/members")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(new AiTeamEndpoint.MemberRequest("bob", AiTeam.TeamRole.MEMBER))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.metadata.name").isEqualTo("team-a");
    }

    @Test
    void removesAMember() {
        var team = team("team-a");
        when(teams.removeMember("team-a", "bob")).thenReturn(Mono.just(team));

        client.delete()
                .uri("/aiteams/team-a/members/bob")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.metadata.name").isEqualTo("team-a");
    }

    @Test
    void rejectsAMissingUserId() {
        client.post()
                .uri("/aiteams/team-a/members")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(new AiTeamEndpoint.MemberRequest("", AiTeam.TeamRole.MEMBER))
                .exchange()
                .expectStatus().isBadRequest();
    }

    private static AiTeam team(String name) {
        var team = new AiTeam();
        var metadata = new Metadata();
        metadata.setName(name);
        team.setMetadata(metadata);
        team.getSpec().setDisplayName(name);
        team.getSpec().setOwnerUserId("alice");
        return team;
    }
}
