package cn.tolim.pudans.core.ai.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.NewConversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.NewMessage;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiConversationServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    AiConversationRepository conversationRepository;

    @Mock
    AiMessageRepository messageRepository;

    private AiConversationService service;

    @BeforeEach
    void setUp() {
        service = new AiConversationService(conversationRepository, messageRepository);
    }

    @Test
    void requireOwnedReturnsTheConversation() {
        when(conversationRepository.findById(7L)).thenReturn(Mono.just(conversation()));

        StepVerifier.create(service.requireOwned(7L, "user-1"))
                .assertNext(found -> assertThat(found.id()).isEqualTo(7L))
                .verifyComplete();
    }

    @Test
    void requireOwnedRejectsAnotherUser() {
        when(conversationRepository.findById(7L)).thenReturn(Mono.just(conversation()));

        StepVerifier.create(service.requireOwned(7L, "user-2"))
                .expectError(ApiException.class)
                .verify();
    }

    @Test
    void requireOwnedRejectsAMissingConversation() {
        when(conversationRepository.findById(7L)).thenReturn(Mono.empty());

        StepVerifier.create(service.requireOwned(7L, "user-1"))
                .expectError(ApiException.class)
                .verify();
    }

    @Test
    void createOrGetReturnsTheExistingConversation() {
        when(conversationRepository.findBySession("default", "user-1", "pudans-chat", "session-1"))
                .thenReturn(Mono.just(conversation()));

        StepVerifier.create(service.createOrGet(newConversation()))
                .assertNext(found -> assertThat(found.id()).isEqualTo(7L))
                .verifyComplete();

        verify(conversationRepository, never()).create(any());
    }

    @Test
    void createOrGetCreatesTheConversationWhenMissing() {
        when(conversationRepository.findBySession("default", "user-1", "pudans-chat", "session-1"))
                .thenReturn(Mono.empty());
        when(conversationRepository.create(newConversation())).thenReturn(Mono.just(conversation()));

        StepVerifier.create(service.createOrGet(newConversation()))
                .assertNext(found -> assertThat(found.sessionKey()).isEqualTo("session-1"))
                .verifyComplete();
    }

    @Test
    void createOrGetFallsBackToTheUniqueSessionLookupAfterAConcurrentCreate() {
        when(conversationRepository.findBySession("default", "user-1", "pudans-chat", "session-1"))
                .thenReturn(Mono.empty(), Mono.just(conversation()));
        when(conversationRepository.create(newConversation())).thenReturn(Mono.empty());

        StepVerifier.create(service.createOrGet(newConversation()))
                .assertNext(found -> assertThat(found.id()).isEqualTo(7L))
                .verifyComplete();
    }

    @Test
    void appendUserMessagePersistsTheUserRoleAndRequest() {
        when(messageRepository.appendMessage(any())).thenReturn(Mono.just(message("user")));

        StepVerifier.create(service.appendUserMessage(conversation(), 2, 0, "如何配置飞书同步？", "req-1"))
                .assertNext(saved -> assertThat(saved.role()).isEqualTo("user"))
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(NewMessage.class);
        verify(messageRepository).appendMessage(captor.capture());
        var captured = captor.getValue();
        assertThat(captured.conversationId()).isEqualTo(7L);
        assertThat(captured.sessionId()).isEqualTo("session-1");
        assertThat(captured.taskId()).isNull();
        assertThat(captured.requestId()).isEqualTo("req-1");
        assertThat(captured.turnIndex()).isEqualTo(2);
        assertThat(captured.messageIndex()).isZero();
        assertThat(captured.role()).isEqualTo("user");
        assertThat(captured.contentType()).isEqualTo("TEXT");
        assertThat(captured.content()).isEqualTo("如何配置飞书同步？");
    }

    @Test
    void appendAssistantMessagePersistsTheModelAndTokenCount() {
        when(messageRepository.appendMessage(any())).thenReturn(Mono.just(message("assistant")));

        StepVerifier.create(service.appendAssistantMessage(
                        conversation(), 2, 1, "飞书同步需要配置应用凭证。", "req-1", "gpt-4o-mini", 128))
                .assertNext(saved -> assertThat(saved.role()).isEqualTo("assistant"))
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(NewMessage.class);
        verify(messageRepository).appendMessage(captor.capture());
        var captured = captor.getValue();
        assertThat(captured.role()).isEqualTo("assistant");
        assertThat(captured.modelCode()).isEqualTo("gpt-4o-mini");
        assertThat(captured.tokenCount()).isEqualTo(128);
        assertThat(captured.messageIndex()).isEqualTo(1);
    }

    @Test
    void appendUserMessageRejectsBlankContent() {
        assertThatThrownBy(() -> service.appendUserMessage(conversation(), 1, 0, " ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("消息内容");

        verifyNoInteractions(messageRepository);
    }

    @Test
    void markPendingReturnsTheUpdatedCount() {
        when(conversationRepository.addPending(7L, 2)).thenReturn(Mono.just(5));

        StepVerifier.create(service.markPending(conversation(), 2))
                .expectNext(5)
                .verifyComplete();
    }

    @Test
    void markPendingRejectsNonPositiveCounts() {
        assertThatThrownBy(() -> service.markPending(conversation(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("待处理消息数");

        verifyNoInteractions(conversationRepository, messageRepository);
    }

    @Test
    void markPendingReportsAMissingConversation() {
        when(conversationRepository.addPending(7L, 1)).thenReturn(Mono.empty());

        StepVerifier.create(service.markPending(conversation(), 1))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("ai-conversation-not-found");
                })
                .verify();
    }

    @Test
    void reducePendingDelegatesThePendingDecrement() {
        when(conversationRepository.reducePending(7L, 2)).thenReturn(Mono.just(1));

        StepVerifier.create(service.reducePending(conversation(), 2))
                .expectNext(1)
                .verifyComplete();
    }

    @Test
    void advanceL1CursorDelegatesTheCursorUpdate() {
        when(conversationRepository.advanceL1Cursor(7L, 42L)).thenReturn(Mono.just(true));

        StepVerifier.create(service.advanceL1Cursor(conversation(), 42L))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void advanceL2CursorDelegatesTheCursorUpdate() {
        when(conversationRepository.advanceL2Cursor(7L, 99L)).thenReturn(Mono.just(false));

        StepVerifier.create(service.advanceL2Cursor(conversation(), 99L))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void advanceL1CursorRejectsNonPositiveMessageIds() {
        assertThatThrownBy(() -> service.advanceL1Cursor(conversation(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("消息 ID");

        verifyNoInteractions(conversationRepository);
    }

    private static NewConversation newConversation() {
        return new NewConversation(
                "default",
                "user-1",
                "team-a",
                "agent-a",
                "pudans-chat",
                "session-1",
                null,
                null,
                "fast",
                "fast-group");
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
                null,
                "fast",
                "fast-group",
                "ACTIVE",
                null,
                null,
                3,
                0L,
                CREATED_AT,
                CREATED_AT,
                CREATED_AT);
    }

    private static Message message(String role) {
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
                2,
                0,
                role,
                "内容",
                "TEXT",
                null,
                null,
                CREATED_AT);
    }
}
