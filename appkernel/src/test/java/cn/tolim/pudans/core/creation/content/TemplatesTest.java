package cn.tolim.pudans.core.creation.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.Template;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.creation.TemplateRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TemplatesTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final RequestIdentity ALICE = new RequestIdentity("alice", "workspace-a");

    @Mock
    TemplateRepository templates;

    private Templates service;

    @BeforeEach
    void setUp() {
        service = new Templates(templates);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void feedKeepsOnlyPublishedPublicTemplates() {
        var draft = template(UUID.randomUUID(), "bob", "draft", "public", null);
        var published = template(UUID.randomUUID(), "bob", "published", "public", NOW);
        when(templates.feed(isNull(), isNull(), eq(21))).thenReturn(Mono.just(List.of(draft, published)));

        StepVerifier.create(service.feed(ALICE, null, null, null))
                .assertNext(feed -> {
                    assertThat(feed.items()).extracting(Template::id).containsExactly(published.id());
                    assertThat(feed.emptyState()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void emptyTemplateFeedReturnsAnEmptyState() {
        when(templates.feed(isNull(), isNull(), eq(21))).thenReturn(Mono.just(List.of()));

        StepVerifier.create(service.feed(ALICE, null, null, null))
                .assertNext(feed -> {
                    assertThat(feed.items()).isEmpty();
                    assertThat(feed.nextCursor()).isNull();
                    assertThat(feed.emptyState()).isNotBlank();
                })
                .verifyComplete();
    }

    @Test
    void hidesAnotherUsersDraftTemplate() {
        var templateId = UUID.randomUUID();
        when(templates.findById(templateId))
                .thenReturn(Mono.just(template(templateId, "bob", "draft", "private", null)));

        StepVerifier.create(service.view(ALICE, templateId))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(404);
                            assertThat(api.getCode()).isEqualTo("template-not-found");
                        }))
                .verify();
    }

    @Test
    void ownerCanViewOwnDraftTemplate() {
        var templateId = UUID.randomUUID();
        when(templates.findById(templateId))
                .thenReturn(Mono.just(template(templateId, "alice", "draft", "private", null)));

        StepVerifier.create(service.view(ALICE, templateId))
                .assertNext(template -> assertThat(template.id()).isEqualTo(templateId))
                .verifyComplete();
    }

    private static Template template(
            UUID id, String owner, String status, String visibility, Instant publishedAt) {
        return new Template(
                id,
                owner,
                "workspace-a",
                "template-" + id,
                "title",
                "description",
                "image",
                "prompt",
                Map.of(),
                Map.of(),
                null,
                List.of(),
                Map.of(),
                status,
                visibility,
                publishedAt,
                NOW,
                NOW);
    }
}
