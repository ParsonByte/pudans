package cn.tolim.pudans.core.creation.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.Template;
import cn.tolim.pudans.contract.creation.Work;
import cn.tolim.pudans.contract.creation.dto.ExploreFeed;
import cn.tolim.pudans.contract.creation.dto.TemplateFeed;
import cn.tolim.pudans.contract.creation.dto.WorkDetail;
import cn.tolim.pudans.contract.creation.dto.WorkInteractionResult;
import cn.tolim.pudans.contract.creation.dto.WorkPlay;
import cn.tolim.pudans.contract.creation.dto.WorkReportRequest;
import cn.tolim.pudans.contract.creation.dto.WorkReportResult;
import cn.tolim.pudans.core.creation.archive.AssetCatalog;
import cn.tolim.pudans.core.creation.content.Templates;
import cn.tolim.pudans.core.creation.content.Works;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ContentEndpointTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final RequestIdentity ALICE = new RequestIdentity("alice", "workspace-a");

    @Mock
    Works works;

    @Mock
    Templates templates;

    @Mock
    AssetCatalog catalog;

    @Mock
    ReactiveRequestIdentity identity;

    @Mock
    CreationAudit audits;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new ContentEndpoint(works, templates, catalog, identity, audits);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void returnsAnEmptyExploreFeedWithoutPublishedContent() {
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(works.exploreFeed(ALICE, null, null, null, null, null, null))
                .thenReturn(Mono.just(new ExploreFeed(List.of(), null, 20, "暂无公开作品。", NOW)));

        client.get()
                .uri("/explore/-/feed")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items")
                .isArray()
                .jsonPath("$.emptyState")
                .isEqualTo("暂无公开作品。");
    }

    @Test
    void listsEveryWorkForAdministrativeInspection() {
        var work = work(UUID.randomUUID());
        when(works.allFeed(null, null, null, null, null, null))
                .thenReturn(Mono.just(new ExploreFeed(List.of(work), null, 20, null, NOW)));

        client.get()
                .uri("/works/-/all")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].ownerId")
                .isEqualTo("bob");
    }

    @Test
    void listsEveryTemplateForAdministrativeInspection() {
        var template = template(UUID.randomUUID());
        when(templates.allFeed(null, null, null))
                .thenReturn(Mono.just(new TemplateFeed(List.of(template), null, 20, null, NOW)));

        client.get()
                .uri("/templates/-/all")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].ownerId")
                .isEqualTo("bob");
    }

    @Test
    void approvesAWorkForAdministrativeReview() {
        var work = work(UUID.randomUUID());
        when(works.approve(work.id())).thenReturn(Mono.just(work));

        client.post()
                .uri("/works/{id}/approve", work.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(work.id().toString());
    }

    @Test
    void publishesATemplateForAdministrativeReview() {
        var template = template(UUID.randomUUID());
        when(templates.publish(template.id())).thenReturn(Mono.just(template));

        client.post()
                .uri("/templates/{id}/publish", template.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(template.id().toString());
    }

    @Test
    void viewsAWorkWithTheCurrentIdentity() {
        var work = work(UUID.randomUUID());
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(works.view(ALICE, work.id())).thenReturn(Mono.just(new WorkDetail(
                work, 1, 2, 3, 4, 5, true, false, true)));

        client.get()
                .uri("/works/{id}/view", work.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.work.id")
                .isEqualTo(work.id().toString())
                .jsonPath("$.liked")
                .isEqualTo(true);
    }

    @Test
    void likesAWorkAndRecordsTheInteractionAudit() {
        var workId = UUID.randomUUID();
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(works.like(ALICE, workId, true))
                .thenReturn(Mono.just(new WorkInteractionResult("like", true, 8)));
        when(audits.record(any(), eq("WORK_LIKE"), eq("Work"), eq(workId.toString()), anyMap()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/works/{id}/-/like", workId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"active\":true}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.interaction")
                .isEqualTo("like")
                .jsonPath("$.count")
                .isEqualTo(8);

        verify(works).like(ALICE, workId, true);
    }

    @Test
    void returnsTheResolvedStorageDownloadUrlForPlayback() {
        var workId = UUID.randomUUID();
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(works.play(ALICE, workId)).thenReturn(Mono.just(new WorkPlay(
                workId.toString(), "file-1", "https://storage.example/file-1", NOW.plusSeconds(300))));

        client.get()
                .uri("/works/{id}/play", workId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.storageFileId")
                .isEqualTo("file-1")
                .jsonPath("$.downloadUrl")
                .isEqualTo("https://storage.example/file-1");
    }

    @Test
    void reportsAWorkWithoutFabricatingAModerationOutcome() {
        var workId = UUID.randomUUID();
        var reportId = UUID.randomUUID();
        var request = new WorkReportRequest("spam", "details");
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(works.report(ALICE, workId, request))
                .thenReturn(Mono.just(new WorkReportResult(reportId, "recorded", NOW)));
        when(audits.record(any(), eq("WORK_REPORT"), eq("Work"), eq(workId.toString()), anyMap()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/works/{id}/-/report", workId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.reportId")
                .isEqualTo(reportId.toString())
                .jsonPath("$.status")
                .isEqualTo("recorded");
    }

    @Test
    void viewsATemplateWithTheTemplateErrorDomain() {
        var template = template(UUID.randomUUID());
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(templates.view(ALICE, template.id())).thenReturn(Mono.just(template));

        client.get()
                .uri("/templates/{id}/view", template.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(template.id().toString());
    }

    private static Work work(UUID id) {
        return new Work(
                id,
                "bob",
                "workspace-b",
                "title",
                "description",
                null,
                null,
                null,
                null,
                null,
                List.of(),
                Map.of(),
                CreationValues.CONTENT_STATUS_PUBLISHED,
                CreationValues.VISIBILITY_PUBLIC,
                1,
                2,
                3,
                4,
                5,
                NOW,
                NOW,
                NOW);
    }

    private static Template template(UUID id) {
        return new Template(
                id,
                "bob",
                "workspace-b",
                "template",
                "title",
                "description",
                "image",
                "prompt",
                Map.of(),
                Map.of(),
                null,
                List.of(),
                Map.of(),
                CreationValues.CONTENT_STATUS_PUBLISHED,
                CreationValues.VISIBILITY_PUBLIC,
                NOW,
                NOW,
                NOW);
    }
}
