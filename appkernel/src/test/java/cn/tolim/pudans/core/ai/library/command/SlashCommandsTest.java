package cn.tolim.pudans.core.ai.library.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiSlashCommand;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.library.command.SlashCommands.ResolvedSlashCommand;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.Arrays;
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
class SlashCommandsTest {

    @Mock
    ReactiveExtensionClient client;

    @InjectMocks
    SlashCommands service;

    @Test
    void expandReplacesPositionalArguments() {
        assertThat(SlashCommands.expand("Review $1 then $2", "alpha beta"))
                .isEqualTo("Review alpha then beta");
    }

    @Test
    void expandReplacesTheArgumentsPlaceholder() {
        assertThat(SlashCommands.expand("Fix: $ARGUMENTS", "src/Main.java"))
                .isEqualTo("Fix: src/Main.java");
    }

    @Test
    void expandReplacesPositionalArgumentsAndThePlaceholderTogether() {
        assertThat(SlashCommands.expand("first=$1 all=$ARGUMENTS", "alpha beta"))
                .isEqualTo("first=alpha all=alpha beta");
    }

    @Test
    void expandLeavesMissingPositionalArgumentsEmpty() {
        assertThat(SlashCommands.expand("$1 $2", "only")).isEqualTo("only ");
    }

    @Test
    void expandDoesNotTreatTwoDigitReferencesAsPositional() {
        assertThat(SlashCommands.expand("$10", "alpha beta")).isEqualTo("$10");
    }

    @Test
    void resolveExpandsTheMatchingCommand() {
        var command = command("review", "Review $1 with $ARGUMENTS");
        when(client.listAll(eq(AiSlashCommand.class), any(), any())).thenReturn(Flux.just(command));

        StepVerifier.create(service.resolve("agent-a", "/review src"))
                .expectNext(new ResolvedSlashCommand("review", "Review src with src"))
                .verifyComplete();
    }

    @Test
    void resolveRejectsAnUnknownCommand() {
        when(client.listAll(eq(AiSlashCommand.class), any(), any())).thenReturn(Flux.empty());

        StepVerifier.create(service.resolve("agent-a", "/missing"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var apiError = (ApiException) error;
                    assertThat(apiError.getCode()).isEqualTo(SlashCommands.CODE_COMMAND_NOT_FOUND);
                    assertThat(apiError.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                })
                .verify();
    }

    @Test
    void resolveRejectsTextWithoutASlash() {
        assertThatThrownBy(() -> service.resolve("agent-a", "review"))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode())
                        .isEqualTo(SlashCommands.CODE_COMMAND_NOT_FOUND));
    }

    @Test
    void resolveRejectsBlankText() {
        assertThatThrownBy(() -> service.resolve("agent-a", " "))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void validateAcceptsAValidCommand() {
        service.validate(command("review", "Review the change."));
    }

    @Test
    void validateRejectsNamesThatDoNotMatchTheCommandPattern() {
        for (var name : Arrays.asList("Review", "-review", "re view", null)) {
            var command = command(name, "Review the change.");

            assertThatThrownBy(() -> service.validate(command))
                    .as("name [%s]", name)
                    .isInstanceOf(ServerWebInputException.class);
        }
    }

    @Test
    void validateRejectsABlankPrompt() {
        var command = command("review", " ");

        assertThatThrownBy(() -> service.validate(command))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void byAgentRejectsABlankAgentName() {
        assertThatThrownBy(() -> service.byAgent(" "))
                .isInstanceOf(ServerWebInputException.class);
    }

    private static AiSlashCommand command(String name, String prompt) {
        var command = new AiSlashCommand();
        var metadata = new Metadata();
        metadata.setName("command-" + name);
        command.setMetadata(metadata);
        command.getSpec().setName(name);
        command.getSpec().setPrompt(prompt);
        command.getSpec().getAgents().add("agent-a");
        return command;
    }
}