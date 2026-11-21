package cn.tolim.pudans.core.ai.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ConversationsPagingTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    AiConversationRepository conversationRepository;

    @Mock
    AiMessageRepository messageRepository;

    private Conversations service;

    @BeforeEach
    void setUp() {
        service = new Conversations(conversationRepository, messageRepository);
    }

    @Test
    void listsConversationsForTheCurrentUser() {
        when(conversationRepository.listByUser("user-1", 30L, 10)).thenReturn(Flux.just(conversation()));

        StepVerifier.create(service.list("user-1", 30L, 10))
                .assertNext(found -> assertThat(found.id()).isEqualTo(7L))
                .verifyComplete();
    }

    @Test
    void rejectsAnInvalidConversationPage() {
        assertThatThrownBy(() -> service.list("user-1", 0L, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分页游标");
        assertThatThrownBy(() -> service.list("user-1", 1L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分页数量");

        verifyNoInteractions(conversationRepository, messageRepository);
    }

    @Test
    void streamsMessagesAfterTheCursor() {
        when(messageRepository.listMessages(7L, 12L, 30)).thenReturn(Flux.just(message()));

        StepVerifier.create(service.history(conversation(), 12L, 30))
                .assertNext(found -> assertThat(found.content()).isEqualTo("内容"))
                .verifyComplete();
    }

    @Test
    void streamsTheMostRecentMessages() {
        when(messageRepository.listRecentMessages(7L, 30)).thenReturn(Flux.just(message()));

        StepVerifier.create(service.recentHistory(conversation(), 30))
                .assertNext(found -> assertThat(found.content()).isEqualTo("内容"))
                .verifyComplete();

        verify(messageRepository).listRecentMessages(7L, 30);
    }

    @Test
    void rejectsANegativeMessageCursor() {
        assertThatThrownBy(() -> service.history(conversation(), -1L, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("消息游标");

        verifyNoInteractions(messageRepository);
    }

    @Test
    void softDeletesTheConversationForItsOwner() {
        when(conversationRepository.softDelete(7L, "user-1")).thenReturn(Mono.just(true));

        StepVerifier.create(service.delete(conversation())).verifyComplete();

        verify(conversationRepository).softDelete(7L, "user-1");
    }

    @Test
    void reportsAMissingConversationOnDelete() {
        when(conversationRepository.softDelete(7L, "user-1")).thenReturn(Mono.just(false));

        StepVerifier.create(service.delete(conversation()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("ai-conversation-not-found");
                })
                .verify();
    }

    private static Conversation conversation() {
        return new Conversation(
                7L,
                "default",
                "user-1",
                "team-a",
                "agent-a",
                "pudans-chat",
                "session-1",
                null,
                "季度复盘",
                "fast",
                "fast-group",
                "ACTIVE",
                null,
                null,
                0,
                0L,
                CREATED_AT,
                CREATED_AT,
                CREATED_AT);
    }

    private static Message message() {
        return new Message(
                11L,
                7L,
                "default",
                "user-1",
                "team-a",
                "agent-a",
                "session-1",
                null,
                "req-1",
                1,
                0,
                "user",
                "内容",
                "TEXT",
                null,
                null,
                CREATED_AT);
    }
}
