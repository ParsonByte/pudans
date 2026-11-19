package cn.tolim.pudans.core.ai.conversation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.conversation.AiConversationService;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.team.AiTeamService;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.NewConversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import java.security.Principal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiConversationEndpointTest {

    private static final String USERNAME = "alice";

    private static final String USER_ID = "uid-alice";

    private static final Principal PRINCIPAL = () -> USERNAME;

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    AiConversationService conversationService;

    @Mock
    AiTeamService teamService;

    @Mock
    AiSettingService settingService;

    @Mock
    UserService userService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AiConversationEndpoint(
                conversationService, teamService, settingService, new AiEndpointRequests(userService));
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter((exchange, chain) -> chain.filter(
                        exchange.mutate().principal(Mono.just(PRINCIPAL)).build()))
                .build();
    }

    @Test
    void listsConversationsWithDefaultPaging() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(conversationService.list(USER_ID, Long.MAX_VALUE, 20)).thenReturn(Flux.just(conversation()));

        client.get()
                .uri("/conversations")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(7)
                .jsonPath("$[0].title").isEqualTo("季度复盘")
                .jsonPath("$[0].agentId").isEqualTo("agent-a");

        verify(conversationService).list(USER_ID, Long.MAX_VALUE, 20);
    }

    @Test
    void appliesTheConversationLimitAndCursor() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(conversationService.list(USER_ID, 42L, 50)).thenReturn(Flux.empty());

        client.get()
                .uri("/conversations?limit=50&beforeId=42")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("[]");

        verify(conversationService).list(USER_ID, 42L, 50);
    }

    @Test
    void rejectsAnOutOfRangeConversationLimit() {
        client.get()
                .uri("/conversations?limit=500")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsANonNumericConversationCursor() {
        client.get()
                .uri("/conversations?beforeId=abc")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void createsAConversationWithTheDefaultAgent() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(settingService.defaults()).thenReturn(Mono.just(new AiSetting.Defaults()));
        when(teamService.requireAgent("default", USER_ID)).thenReturn(Mono.just(agent("default", "default")));
        when(conversationService.createOrGet(any(NewConversation.class))).thenReturn(Mono.just(conversation()));

        client.post()
                .uri("/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AiConversationEndpoint.CreateConversationRequest(null, "新会话", "fast", null))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(7);

        var captor = ArgumentCaptor.forClass(NewConversation.class);
        verify(conversationService).createOrGet(captor.capture());
        var created = captor.getValue();
        assertThat(created.spaceId()).isEqualTo("default");
        assertThat(created.userId()).isEqualTo(USER_ID);
        assertThat(created.teamId()).isEqualTo("default");
        assertThat(created.agentId()).isEqualTo("default");
        assertThat(created.agentSource()).isEqualTo("pudans-chat");
        assertThat(created.sessionKey()).isNotBlank();
        assertThat(created.title()).isEqualTo("新会话");
        assertThat(created.modeCode()).isEqualTo("fast");
        assertThat(created.routeGroup()).isNull();
    }

    @Test
    void createsAConversationWithAnExplicitAgent() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(teamService.requireAgent("research", USER_ID)).thenReturn(Mono.just(agent("research", "team-x")));
        when(conversationService.createOrGet(any(NewConversation.class))).thenReturn(Mono.just(conversation()));

        client.post()
                .uri("/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AiConversationEndpoint.CreateConversationRequest("research", null, null, "pro-group"))
                .exchange()
                .expectStatus().isOk();

        var captor = ArgumentCaptor.forClass(NewConversation.class);
        verify(conversationService).createOrGet(captor.capture());
        assertThat(captor.getValue().agentId()).isEqualTo("research");
        assertThat(captor.getValue().teamId()).isEqualTo("team-x");
        assertThat(captor.getValue().routeGroup()).isEqualTo("pro-group");
        verify(settingService, never()).defaults();
    }

    @Test
    void getsAnOwnedConversation() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(conversationService.requireOwned(7L, USER_ID)).thenReturn(Mono.just(conversation()));

        client.get()
                .uri("/conversations/7")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(7);
    }

    @Test
    void returnsForbiddenForAnotherOwnersConversation() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(conversationService.requireOwned(7L, USER_ID))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.FORBIDDEN, "ai-conversation-forbidden", "无权访问该会话。")));

        client.get()
                .uri("/conversations/7")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void returnsNotFoundForANonNumericConversationName() {
        client.get()
                .uri("/conversations/abc")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void softDeletesAnOwnedConversation() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(conversationService.requireOwned(7L, USER_ID)).thenReturn(Mono.just(conversation()));
        when(conversationService.delete(conversation())).thenReturn(Mono.empty());

        client.delete()
                .uri("/conversations/7")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void listsMessagesWithTheDefaultCursor() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(conversationService.requireOwned(7L, USER_ID)).thenReturn(Mono.just(conversation()));
        when(conversationService.history(conversation(), 0L, 50)).thenReturn(Flux.just(message()));
        when(conversationService.metadata(conversation())).thenReturn(Flux.empty());

        client.get()
                .uri("/conversations/7/messages")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].role").isEqualTo("user")
                .jsonPath("$[0].content").isEqualTo("如何配置飞书同步？");

        verify(conversationService).history(conversation(), 0L, 50);
    }

    @Test
    void appliesTheMessageCursorAndLimit() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(conversationService.requireOwned(7L, USER_ID)).thenReturn(Mono.just(conversation()));
        when(conversationService.history(conversation(), 12L, 100)).thenReturn(Flux.empty());
        when(conversationService.metadata(conversation())).thenReturn(Flux.empty());

        client.get()
                .uri("/conversations/7/messages?afterId=12&limit=100")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("[]");

        verify(conversationService).history(conversation(), 12L, 100);
    }

    private static User user() {
        var user = new User();
        user.getSpec().setUid(USER_ID);
        return user;
    }

    private static AiAgent agent(String name, String team) {
        var agent = new AiAgent();
        var metadata = new Metadata();
        metadata.setName(name);
        agent.setMetadata(metadata);
        agent.getSpec().setTeam(team);
        agent.getSpec().setOwnerUserId(USER_ID);
        return agent;
    }

    private static Conversation conversation() {
        return new Conversation(
                7L,
                "default",
                USER_ID,
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
                USER_ID,
                "team-a",
                "agent-a",
                "session-1",
                null,
                "req-1",
                1,
                0,
                "user",
                "如何配置飞书同步？",
                "TEXT",
                null,
                null,
                CREATED_AT);
    }
}
