package cn.tolim.pudans.setting.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.setting.MailSettings;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import cn.tolim.pudans.security.authentication.AuthenticationConnectionTester;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class ParameterSettingEndpointTest {

    @Mock
    ParameterGroups groupService;

    @Mock
    MailSettings mailSettings;

    @Mock
    AuthenticationConnectionTester connectionTester;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        var endpoint = new ParameterSettingEndpoint(groupService, mailSettings, connectionTester);
        webTestClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void authenticationConnectionTestReturnsTheResult() {
        when(connectionTester.test(any()))
                .thenReturn(Mono.just(
                        new AuthenticationConnectionTester.ConnectionTestResult(true, "ok")));

        webTestClient
                .post()
                .uri("/parameter-settings/authentication/-/test")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("type", "feishu"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.message")
                .isEqualTo("ok");
    }

    @Test
    void newPathGetReturnsTheGroupValue() {
        when(groupService.getGroup("site")).thenReturn(Mono.just(siteNode()));

        webTestClient
                .get()
                .uri("/parameter-settings/site")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.siteName")
                .isEqualTo("Pudans");
    }

    @Test
    void newPathPutStoresTheGroupValueAndReportsRestart() {
        when(groupService.updateGroup(eq("server"), any(ObjectNode.class)))
                .thenReturn(Mono.just(new ParameterGroups.UpdateResult(true)));

        webTestClient
                .put()
                .uri("/parameter-settings/server")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("masterKey", "rotated"))
                .exchange()
                .expectStatus()
                .isNoContent()
                .expectHeader()
                .valueEquals(ParameterSettingEndpoint.RESTART_REQUIRED_HEADER, "true");

        verify(groupService).updateGroup(eq("server"), any(ObjectNode.class));
    }

    @Test
    void legacyPathGetRemainsAvailable() {
        when(groupService.getLegacyGroup("basic")).thenReturn(Mono.just(siteNode()));

        webTestClient
                .get()
                .uri("/systemconfigs/basic")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.siteName")
                .isEqualTo("Pudans");
    }

    @Test
    void legacyPathPutRemainsAvailable() {
        when(groupService.updateLegacyGroup(eq("basic"), any(ObjectNode.class)))
                .thenReturn(Mono.just(new ParameterGroups.UpdateResult(false)));

        webTestClient
                .put()
                .uri("/systemconfigs/basic")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("title", "Pudans"))
                .exchange()
                .expectStatus()
                .isNoContent();

        var captor = ArgumentCaptor.forClass(ObjectNode.class);
        verify(groupService).updateLegacyGroup(eq("basic"), captor.capture());
        assertThat(captor.getValue().get("title").asString()).isEqualTo("Pudans");
    }

    @Test
    void testMailEndpointReturnsNoContentOnSuccess() {
        when(mailSettings.sendTest("alice@example.com")).thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri("/parameter-settings/mail/-/test")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("to", "alice@example.com"))
                .exchange()
                .expectStatus()
                .isNoContent();

        verify(mailSettings).sendTest("alice@example.com");
    }

    @Test
    void testMailEndpointReturnsServiceUnavailableWhenSmtpFails() {
        when(mailSettings.sendTest("alice@example.com"))
                .thenReturn(Mono.error(new ApiException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "mail-send-failed",
                        "Could not send the email.")));

        webTestClient
                .post()
                .uri("/parameter-settings/mail/-/test")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("to", "alice@example.com"))
                .exchange()
                .expectStatus()
                .isEqualTo(503);
    }

    @Test
    void testMailEndpointRejectsAnInvalidAddress() {
        webTestClient
                .post()
                .uri("/parameter-settings/mail/-/test")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("to", "not-an-email"))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    private static ObjectNode siteNode() {
        var node = JsonMapper.shared().createObjectNode();
        node.put("siteName", "Pudans");
        return node;
    }
}
