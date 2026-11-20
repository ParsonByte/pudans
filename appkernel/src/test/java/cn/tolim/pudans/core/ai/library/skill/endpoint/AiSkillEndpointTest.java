package cn.tolim.pudans.core.ai.library.skill.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiSkill;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.library.skill.SkillService;
import cn.tolim.pudans.core.ai.team.AiTeamService;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiSkillEndpointTest {

    @Mock
    SkillService skillService;

    @Mock
    AiTeamService teamService;

    @Mock
    AiEndpointRequests requests;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToRouterFunction(
                        new AiSkillEndpoint(skillService, teamService, requests).endpoint())
                .build();
    }

    @Test
    void importsASkillDraft() {
        when(requests.userId(any())).thenReturn(Mono.just("alice"));
        when(skillService.importFromUrl("https://example.com/SKILL.md", "main"))
                .thenReturn(Mono.just(skill("release")));

        client.post()
                .uri("/aiskills/-/import")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AiSkillEndpoint.SkillImportRequest(
                        "https://example.com/SKILL.md", "main"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.spec.displayName").isEqualTo("release")
                .jsonPath("$.spec.source").isEqualTo("URL");
    }

    @Test
    void listsSkillsForAnOwnedAgent() {
        when(requests.userId(any())).thenReturn(Mono.just("alice"));
        when(teamService.requireAgent("agent-a", "alice")).thenReturn(Mono.just(agent("agent-a")));
        when(skillService.byAgent("agent-a")).thenReturn(Flux.just(skill("release")));

        client.get()
                .uri("/aiskills/-/by-agent?agent=agent-a")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].metadata.name").isEqualTo("release")
                .jsonPath("$[0].spec.displayName").isEqualTo("release");
    }

    @Test
    void rejectsAMissingAgentParameter() {
        client.get()
                .uri("/aiskills/-/by-agent")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void forbidsAnAgentOutsideTheUserTeam() {
        when(requests.userId(any())).thenReturn(Mono.just("alice"));
        when(teamService.requireAgent("agent-a", "alice"))
                .thenReturn(Mono.error(new AccessDeniedException("无权访问该 Agent。")));

        client.get()
                .uri("/aiskills/-/by-agent?agent=agent-a")
                .exchange()
                .expectStatus().isForbidden();
    }

    private static AiSkill skill(String name) {
        var skill = new AiSkill();
        var metadata = new Metadata();
        metadata.setName(name);
        skill.setMetadata(metadata);
        skill.getSpec().setDisplayName(name);
        skill.getSpec().setSource(AiSkill.Source.URL);
        skill.getSpec().setUrl("https://example.com/SKILL.md");
        skill.getSpec().setContent("body");
        return skill;
    }

    private static AiAgent agent(String name) {
        var agent = new AiAgent();
        var metadata = new Metadata();
        metadata.setName(name);
        agent.setMetadata(metadata);
        agent.getSpec().setDisplayName(name);
        return agent;
    }
}