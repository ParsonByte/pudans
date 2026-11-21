package cn.tolim.pudans.core.infra.r2dbc.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.harness.agent.AgentState;
import cn.tolim.pudans.core.ai.harness.agent.AgentStateCodec;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class R2dbcAgentStateStoreTest {

    @Mock
    AiAgentStateRepository repository;

    @Test
    void loadsTheStoredStateAndVersion() {
        var state = new AgentState();
        state.addMessage(UserMessage.from("hello"));
        when(repository.find("user-1", "session-1"))
                .thenReturn(Mono.just(new AiAgentStateRepository.StateRow(
                        AgentStateCodec.encode(state), 3L)));
        var store = new R2dbcAgentStateStore(repository);

        StepVerifier.create(store.loadVersioned("user-1", "session-1"))
                .assertNext(versioned -> {
                    assertThat(versioned.version()).isEqualTo(3);
                    assertThat(versioned.state().messages()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    void savesAndDeletesState() {
        when(repository.save(eq("user-1"), eq("session-1"), anyString())).thenReturn(Mono.just(1L));
        when(repository.delete("user-1", "session-1")).thenReturn(Mono.empty());
        var store = new R2dbcAgentStateStore(repository);

        StepVerifier.create(store.save("user-1", "session-1", new AgentState())).verifyComplete();
        StepVerifier.create(store.delete("user-1", "session-1")).verifyComplete();

        verify(repository).save(eq("user-1"), eq("session-1"), anyString());
        verify(repository).delete("user-1", "session-1");
    }

    @Test
    void savesWithCasVersion() {
        when(repository.saveIfVersion(eq("user-1"), eq("session-1"), anyString(), eq(3L)))
                .thenReturn(Mono.just(4L));
        var store = new R2dbcAgentStateStore(repository);

        StepVerifier.create(store.saveIfVersion("user-1", "session-1", new AgentState(), 3L))
                .expectNext(4L)
                .verifyComplete();
    }
}