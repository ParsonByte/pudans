package cn.tolim.pudans.core.ai.library.subagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiSubagent;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SubagentServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @InjectMocks
    SubagentService service;

    @Test
    void resolveReturnsTheNamedSubagent() {
        var subagent = subagent("reviewer");
        when(client.listAll(eq(AiSubagent.class), any(), any())).thenReturn(Flux.just(subagent));

        StepVerifier.create(service.resolve("agent-a", "reviewer"))
                .expectNext(subagent)
                .verifyComplete();
    }

    @Test
    void resolveRejectsAnUnknownSubagent() {
        when(client.listAll(eq(AiSubagent.class), any(), any())).thenReturn(Flux.empty());

        StepVerifier.create(service.resolve("agent-a", "missing"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var apiError = (ApiException) error;
                    assertThat(apiError.getCode()).isEqualTo(SubagentService.CODE_SUBAGENT_NOT_FOUND);
                    assertThat(apiError.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                })
                .verify();
    }

    @Test
    void resolveRejectsABlankName() {
        StepVerifier.create(service.resolve("agent-a", " "))
                .expectError(ServerWebInputException.class)
                .verify();
    }

    @Test
    void validateAcceptsAValidSubagent() {
        service.validate(subagent("reviewer"));
    }

    @Test
    void validateRejectsABlankDisplayName() {
        var subagent = subagent("reviewer");
        subagent.getSpec().setDisplayName("");

        assertThatThrownBy(() -> service.validate(subagent))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void validateRejectsABlankPrompt() {
        var subagent = subagent("reviewer");
        subagent.getSpec().setPrompt(null);

        assertThatThrownBy(() -> service.validate(subagent))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void validateRejectsBlankSkillEntries() {
        var subagent = subagent("reviewer");
        subagent.getSpec().setSkills(List.of("review", " "));

        assertThatThrownBy(() -> service.validate(subagent))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void validateRejectsBlankMcpServerEntries() {
        var subagent = subagent("reviewer");
        subagent.getSpec().setMcpServers(List.of(""));

        assertThatThrownBy(() -> service.validate(subagent))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void byAgentRejectsABlankAgentName() {
        assertThatThrownBy(() -> service.byAgent(" "))
                .isInstanceOf(ServerWebInputException.class);
    }

    private static AiSubagent subagent(String name) {
        var subagent = new AiSubagent();
        var metadata = new Metadata();
        metadata.setName(name);
        subagent.setMetadata(metadata);
        subagent.getSpec().setDisplayName("Subagent " + name);
        subagent.getSpec().setPrompt("Review the change.");
        subagent.getSpec().setSkills(List.of("review"));
        subagent.getSpec().setMcpServers(List.of("github"));
        subagent.getSpec().getAgents().add("agent-a");
        return subagent;
    }
}