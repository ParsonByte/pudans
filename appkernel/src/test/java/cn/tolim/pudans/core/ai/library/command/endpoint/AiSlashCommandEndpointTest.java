package cn.tolim.pudans.core.ai.library.command.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiAgent;
import cn.tolim.pudans.contract.ai.resource.AiSlashCommand;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.library.command.SlashCommands;
import cn.tolim.pudans.core.ai.library.command.SlashCommands.ResolvedSlashCommand;
import cn.tolim.pudans.core.ai.team.Teams;
import cn.tolim.pudans.infra.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiSlashCommandEndpointTest {

    @Mock
    SlashCommands slashCommands;

    @Mock
    AiEndpointRequests requests;

    @Mock
    Teams teamService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        lenient().when(requests.userId(any())).thenReturn(Mono.just("user-1"));
        lenient().when(teamService.requireAgent(any(), any())).thenReturn(Mono.just(new AiAgent()));
        client = WebTestClient.bindToRouterFunction(
                        new AiSlashCommandEndpoint(slashCommands, requests, teamService)
                                .endpoint())
                .build();
    }

    @Test
    void listsSlashCommandsBoundToTheAgent() {
        when(slashCommands.byAgent("agent-a")).thenReturn(Flux.just(command()));

        client.get()
                .uri("/aislashcommands/-/by-agent?agent=agent-a")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].metadata.name").isEqualTo("command-review")
                .jsonPath("$[0].spec.name").isEqualTo("review");
    }

    @Test
    void resolvesASlashCommand() {
        when(slashCommands.resolve("agent-a", "/review src"))
                .thenReturn(Mono.just(new ResolvedSlashCommand("review", "Review src")));

        client.post()
                .uri("/aislashcommands/-/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"agent\":\"agent-a\",\"text\":\"/review src\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("review")
                .jsonPath("$.expandedPrompt").isEqualTo("Review src");
    }

    @Test
    void rejectsAMissingAgent() {
        client.post()
                .uri("/aislashcommands/-/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"text\":\"/review src\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsBlankText() {
        client.post()
                .uri("/aislashcommands/-/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"agent\":\"agent-a\",\"text\":\" \"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void mapsAnUnknownCommandToNotFound() {
        when(slashCommands.resolve("agent-a", "/missing"))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND,
                        SlashCommands.CODE_COMMAND_NOT_FOUND,
                        "未知斜杠命令：missing")));

        client.post()
                .uri("/aislashcommands/-/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"agent\":\"agent-a\",\"text\":\"/missing\"}")
                .exchange()
                .expectStatus().isNotFound();
    }

    private static AiSlashCommand command() {
        var command = new AiSlashCommand();
        var metadata = new Metadata();
        metadata.setName("command-review");
        command.setMetadata(metadata);
        command.getSpec().setName("review");
        command.getSpec().setPrompt("Review $ARGUMENTS");
        command.getSpec().getAgents().add("agent-a");
        return command;
    }
}