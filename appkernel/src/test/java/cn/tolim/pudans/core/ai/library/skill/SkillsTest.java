package cn.tolim.pudans.core.ai.library.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiSkill;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.infra.exception.ApiException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SkillsTest {

    @Mock
    ReactiveExtensionClient client;

    private Skills service;

    @BeforeEach
    void setUp() {
        service = service(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .body("")
                .build()));
    }

    @Test
    void parsesFrontmatterNameAndDescription() {
        var parsed = Skills.parseFrontmatter("---\nname: Release Notes\n"
                + "description: \"Write release notes\"\n---\n# Body");

        assertThat(parsed).isNotNull();
        assertThat(parsed.name()).isEqualTo("Release Notes");
        assertThat(parsed.description()).isEqualTo("Write release notes");
    }

    @Test
    void fallsBackToTheFileNameWhenFrontmatterIsMissing() {
        assertThat(Skills.parseFrontmatter("# no frontmatter")).isNull();
        assertThat(Skills.fallbackName(URI.create("https://example.com/skills/release.md")))
                .isEqualTo("release");
        assertThat(Skills.fallbackName(URI.create("https://example.com/")))
                .isEqualTo("example.com");
    }

    @Test
    void importsDraftWithFrontmatterAndRef() {
        var content = "---\nname: Release Notes\ndescription: Write release notes\n---\n# Body";
        service = service(textResponse(MediaType.parseMediaType("text/markdown"), content));

        StepVerifier.create(service.importFromUrl("https://example.com/skills/SKILL.md", "main"))
                .assertNext(draft -> {
                    var spec = draft.getSpec();
                    assertThat(spec.getDisplayName()).isEqualTo("Release Notes");
                    assertThat(spec.getDescription()).isEqualTo("Write release notes");
                    assertThat(spec.getSource()).isEqualTo(AiSkill.Source.URL);
                    assertThat(spec.getUrl()).isEqualTo("https://example.com/skills/SKILL.md");
                    assertThat(spec.getRef()).isEqualTo("main");
                    assertThat(spec.getContent()).isEqualTo(content);
                    assertThat(spec.isEnabled()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void fallsBackToTheFileNameWhenImportingWithoutFrontmatter() {
        service = service(textResponse(MediaType.TEXT_PLAIN, "# Skill body"));

        StepVerifier.create(service.importFromUrl("https://example.com/skills/release.md", null))
                .assertNext(draft -> assertThat(draft.getSpec().getDisplayName()).isEqualTo("release"))
                .verifyComplete();
    }

    @Test
    void rejectsUnsupportedImportUrls() {
        StepVerifier.create(service.importFromUrl("file:///etc/passwd", null))
                .expectErrorSatisfies(error -> {
                    var apiException = assertApiException(error, 400);
                    assertThat(apiException.getCode()).isEqualTo(Skills.CODE_IMPORT_INVALID);
                })
                .verify();
    }

    @Test
    void rejectsNonTextResponses() {
        service = service(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{}")
                .build()));

        StepVerifier.create(service.importFromUrl("https://example.com/skill.md", null))
                .expectErrorSatisfies(error -> {
                    var apiException = assertApiException(error, 415);
                    assertThat(apiException.getCode()).isEqualTo(Skills.CODE_IMPORT_UNSUPPORTED);
                })
                .verify();
    }

    @Test
    void rejectsOversizedResponses() {
        var oversized = "x".repeat(Skills.MAX_CONTENT_BYTES + 1);
        service = service(textResponse(MediaType.TEXT_PLAIN, oversized));

        StepVerifier.create(service.importFromUrl("https://example.com/skill.md", null))
                .expectErrorSatisfies(error -> {
                    var apiException = assertApiException(error, 413);
                    assertThat(apiException.getCode()).isEqualTo(Skills.CODE_IMPORT_TOO_LARGE);
                })
                .verify();
    }

    @Test
    void mapsDownloadFailuresToGatewayErrors() {
        service = service(request -> Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build()));

        StepVerifier.create(service.importFromUrl("https://example.com/skill.md", null))
                .expectErrorSatisfies(error -> {
                    var apiException = assertApiException(error, 502);
                    assertThat(apiException.getCode()).isEqualTo(Skills.CODE_IMPORT_FAILED);
                })
                .verify();
    }

    @Test
    void filtersByEnabledAndAgentBinding() {
        var bound = skill("skill-a", true, "agent-a");
        var disabled = skill("skill-b", false, "agent-a");
        var otherAgent = skill("skill-c", true, "agent-b");
        when(client.listAll(eq(AiSkill.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(bound, disabled, otherAgent));

        StepVerifier.create(service.byAgent("agent-a"))
                .assertNext(skill -> assertThat(skill.getMetadata().getName()).isEqualTo("skill-a"))
                .verifyComplete();
    }

    @Test
    void selectsEnabledSkillsByExplicitNames() {
        var first = skill("skill-a", true, "agent-a");
        var second = skill("skill-b", true, "agent-b");
        when(client.listAll(eq(AiSkill.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(first, second));

        StepVerifier.create(service.byNames(List.of("skill-b")))
                .assertNext(skills -> assertThat(skills)
                        .extracting(skill -> skill.getMetadata().getName())
                        .containsExactly("skill-b"))
                .verifyComplete();
    }

    @Test
    void injectsNameDescriptionAndContentSummaries() {
        var bound = skill("skill-a", true, "agent-a");
        when(client.listAll(eq(AiSkill.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(bound));

        StepVerifier.create(service.skillsForAgent("agent-a"))
                .assertNext(summary -> {
                    assertThat(summary.name()).isEqualTo("skill-a");
                    assertThat(summary.displayName()).isEqualTo("skill-a");
                    assertThat(summary.description()).isEqualTo("描述 skill-a");
                    assertThat(summary.content()).isEqualTo("skill-a 内容");
                })
                .verifyComplete();
    }

    @Test
    void requiresInlineContent() {
        var skill = new AiSkill();
        skill.getSpec().setDisplayName("inline");
        skill.getSpec().setSource(AiSkill.Source.INLINE);

        StepVerifier.create(Skills.validate(skill))
                .expectErrorSatisfies(error -> {
                    var apiException = assertApiException(error, 400);
                    assertThat(apiException.getCode()).isEqualTo(Skills.CODE_INVALID);
                    assertThat(apiException.getReason()).contains("content");
                })
                .verify();
    }

    @Test
    void requiresUrlForRemoteSources() {
        var skill = new AiSkill();
        skill.getSpec().setDisplayName("remote");
        skill.getSpec().setSource(AiSkill.Source.GIT);

        StepVerifier.create(Skills.validate(skill))
                .expectErrorSatisfies(error -> assertThat(((ApiException) error).getReason())
                        .contains("url"))
                .verify();
    }

    private Skills service(ExchangeFunction exchange) {
        return new Skills(client, WebClient.builder().exchangeFunction(exchange).build());
    }

    private static ExchangeFunction textResponse(MediaType contentType, String body) {
        return request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, contentType.toString())
                .body(body)
                .build());
    }

    private static ApiException assertApiException(Throwable error, int status) {
        assertThat(error).isInstanceOf(ApiException.class);
        var apiException = (ApiException) error;
        assertThat(apiException.getStatusCode().value()).isEqualTo(status);
        return apiException;
    }

    private static AiSkill skill(String name, boolean enabled, String agent) {
        var skill = new AiSkill();
        var metadata = new Metadata();
        metadata.setName(name);
        skill.setMetadata(metadata);
        skill.getSpec().setDisplayName(name);
        skill.getSpec().setDescription("描述 " + name);
        skill.getSpec().setContent(name + " 内容");
        skill.getSpec().setSource(AiSkill.Source.INLINE);
        skill.getSpec().setEnabled(enabled);
        skill.getSpec().getAgents().add(agent);
        return skill;
    }
}