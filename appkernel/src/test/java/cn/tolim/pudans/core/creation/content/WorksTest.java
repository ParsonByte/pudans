package cn.tolim.pudans.core.creation.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.Asset;
import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.Work;
import cn.tolim.pudans.contract.creation.dto.WorkReportRequest;
import cn.tolim.pudans.contract.creation.dto.WorkRemixRequest;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.download.DownloadUrl;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.creation.AssetRepository;
import cn.tolim.pudans.infra.r2dbc.creation.InteractionRepository;
import cn.tolim.pudans.infra.r2dbc.creation.WorkRepository;
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
class WorksTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final RequestIdentity ALICE = new RequestIdentity("alice", "workspace-a");

    @Mock
    WorkRepository works;

    @Mock
    InteractionRepository interactions;

    @Mock
    AssetRepository assets;

    @Mock
    StorageTransfer storageTransfer;

    private Works service;

    @BeforeEach
    void setUp() {
        service = new Works(works, interactions, assets, storageTransfer);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void hidesAnotherUsersDraft() {
        var workId = UUID.randomUUID();
        when(works.findById(workId)).thenReturn(Mono.just(work(
                workId, "bob", "workspace-a", "draft", "private", null, 0, 0)));

        StepVerifier.create(service.view(ALICE, workId))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(404);
                            assertThat(api.getCode()).isEqualTo("work-not-found");
                        }))
                .verify();
    }

    @Test
    void allowsTheOwnerToViewADraftWithoutIncrementingPublicViews() {
        var workId = UUID.randomUUID();
        when(works.findById(workId)).thenReturn(Mono.just(work(
                workId, "alice", "workspace-a", "draft", "private", null, 4, 5)));
        when(interactions.exists(workId, "alice", CreationValues.INTERACTION_LIKE))
                .thenReturn(Mono.just(true));
        when(interactions.exists(workId, "alice", CreationValues.INTERACTION_FAVORITE))
                .thenReturn(Mono.just(false));
        when(interactions.isFollowing("alice", "alice")).thenReturn(Mono.just(false));

        StepVerifier.create(service.view(ALICE, workId))
                .assertNext(detail -> {
                    assertThat(detail.viewCount()).isEqualTo(4);
                    assertThat(detail.likeCount()).isEqualTo(5);
                    assertThat(detail.liked()).isTrue();
                    assertThat(detail.favorited()).isFalse();
                })
                .verifyComplete();

        verify(interactions, never()).incrementView(any());
    }

    @Test
    void publicFeedDropsRowsThatAreNotPublishedAndPublic() {
        var draft = work(UUID.randomUUID(), "bob", "workspace-b", "draft", "public", null, 0, 0);
        var published = work(
                UUID.randomUUID(), "bob", "workspace-b", "published", "public", NOW, 1, 1);
        when(works.feed(isNull(), isNull(), isNull(), isNull(), isNull(), eq(21)))
                .thenReturn(Mono.just(List.of(draft, published)));

        StepVerifier.create(service.exploreFeed(ALICE, null, null, null, null, null, null))
                .assertNext(feed -> {
                    assertThat(feed.items()).extracting(Work::id).containsExactly(published.id());
                    assertThat(feed.emptyState()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void allFeedIncludesEveryStatusAndVisibility() {
        var draft = work(UUID.randomUUID(), "bob", "workspace-b", "draft", "private", null, 0, 0);
        var published = work(
                UUID.randomUUID(), "bob", "workspace-b", "published", "public", NOW, 1, 1);
        when(works.allFeed(isNull(), isNull(), isNull(), isNull(), isNull(), eq(21)))
                .thenReturn(Mono.just(List.of(draft, published)));

        StepVerifier.create(service.allFeed(null, null, null, null, null, null))
                .assertNext(feed -> {
                    assertThat(feed.items()).extracting(Work::id)
                            .containsExactly(draft.id(), published.id());
                    assertThat(feed.emptyState()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void emptyFeedHasAnEmptyStateAndNoCursor() {
        when(works.feed(isNull(), isNull(), isNull(), isNull(), isNull(), eq(21)))
                .thenReturn(Mono.just(List.of()));

        StepVerifier.create(service.exploreFeed(ALICE, null, null, null, null, null, null))
                .assertNext(feed -> {
                    assertThat(feed.items()).isEmpty();
                    assertThat(feed.nextCursor()).isNull();
                    assertThat(feed.emptyState()).isNotBlank();
                })
                .verifyComplete();
    }

    @Test
    void approvesAndPublishesAWorkThroughTheAdministrativeTransitions() {
        var workId = UUID.randomUUID();
        var draft = work(workId, "bob", "workspace-b", "draft", "private", null, 0, 0);
        var approved = work(workId, "bob", "workspace-b", "approved", "private", null, 0, 0);
        var published = work(workId, "bob", "workspace-b", "published", "public", NOW, 0, 0);
        when(works.findById(workId)).thenReturn(Mono.just(draft));
        when(works.updateStatus(
                        workId,
                        CreationValues.CONTENT_STATUS_APPROVED,
                        CreationValues.VISIBILITY_PRIVATE,
                        null,
                        NOW))
                .thenReturn(Mono.just(approved));

        StepVerifier.create(service.approve(workId))
                .assertNext(result -> assertThat(result.status()).isEqualTo("approved"))
                .verifyComplete();

        when(works.findById(workId)).thenReturn(Mono.just(approved));
        when(works.updateStatus(
                        workId,
                        CreationValues.CONTENT_STATUS_PUBLISHED,
                        CreationValues.VISIBILITY_PUBLIC,
                        NOW,
                        NOW))
                .thenReturn(Mono.just(published));

        StepVerifier.create(service.publish(workId))
                .assertNext(result -> assertThat(result.status()).isEqualTo("published"))
                .verifyComplete();
    }

    @Test
    void rejectsPublishingAWorkThatHasNotBeenApproved() {
        var workId = UUID.randomUUID();
        when(works.findById(workId)).thenReturn(Mono.just(work(
                workId, "bob", "workspace-b", "draft", "private", null, 0, 0)));

        StepVerifier.create(service.publish(workId))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(409);
                            assertThat(api.getCode()).isEqualTo("work-not-approved");
                        }))
                .verify();
    }

    @Test
    void explicitLikeIsIdempotentWhenTheInteractionAlreadyExists() {
        var workId = UUID.randomUUID();
        when(works.findById(workId)).thenReturn(Mono.just(work(
                workId, "bob", "workspace-b", "published", "public", NOW, 1, 7)));
        when(interactions.exists(workId, "alice", CreationValues.INTERACTION_LIKE))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.like(ALICE, workId, true))
                .assertNext(result -> {
                    assertThat(result.active()).isTrue();
                    assertThat(result.count()).isEqualTo(7);
                })
                .verifyComplete();

        verify(interactions, never()).addLike(any(), any(), any(), any());
        verify(interactions, never()).removeLike(any(), any(), any(), any());
    }

    @Test
    void explicitLikeAddsTheUniqueInteractionWhenMissing() {
        var workId = UUID.randomUUID();
        when(works.findById(workId)).thenReturn(Mono.just(work(
                workId, "bob", "workspace-b", "published", "public", NOW, 0, 0)));
        when(interactions.exists(workId, "alice", CreationValues.INTERACTION_LIKE))
                .thenReturn(Mono.just(false));
        when(interactions.addLike(eq(workId), eq("alice"), eq("workspace-a"), eq(NOW)))
                .thenReturn(Mono.just(new InteractionRepository.InteractionCount(true, 1)));

        StepVerifier.create(service.like(ALICE, workId, true))
                .assertNext(result -> {
                    assertThat(result.active()).isTrue();
                    assertThat(result.count()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void favoriteToggleRemovesAnExistingFavorite() {
        var workId = UUID.randomUUID();
        when(works.findById(workId)).thenReturn(Mono.just(work(
                workId, "bob", "workspace-b", "published", "public", NOW, 0, 2)));
        when(interactions.exists(workId, "alice", CreationValues.INTERACTION_FAVORITE))
                .thenReturn(Mono.just(true));
        when(interactions.removeFavorite(eq(workId), eq("alice"), eq("workspace-a"), eq(NOW)))
                .thenReturn(Mono.just(new InteractionRepository.InteractionCount(false, 1)));

        StepVerifier.create(service.favorite(ALICE, workId, null))
                .assertNext(result -> {
                    assertThat(result.active()).isFalse();
                    assertThat(result.count()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void playFailsExplicitlyWhenTheWorkHasNoPrimaryAsset() {
        var workId = UUID.randomUUID();
        when(works.findById(workId)).thenReturn(Mono.just(work(
                workId, "bob", "workspace-b", "published", "public", NOW, 0, 0, null)));

        StepVerifier.create(service.play(ALICE, workId))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(409);
                            assertThat(api.getCode()).isEqualTo("work-play-not-ready");
                        }))
                .verify();
    }

    @Test
    void playResolvesThePrimaryAssetThroughTheStorageDownloadUrl() {
        var workId = UUID.randomUUID();
        var assetId = UUID.randomUUID();
        var expiresAt = NOW.plusSeconds(300);
        when(works.findById(workId)).thenReturn(Mono.just(work(
                workId, "bob", "workspace-b", "published", "public", NOW, 0, 0, assetId)));
        when(assets.findById(assetId)).thenReturn(Mono.just(asset(
                assetId, "bob", "workspace-b", "file-1")));
        when(storageTransfer.downloadUrl("file-1", null))
                .thenReturn(Mono.just(new DownloadUrl("https://storage.example/file-1", expiresAt)));

        StepVerifier.create(service.play(ALICE, workId))
                .assertNext(play -> {
                    assertThat(play.storageFileId()).isEqualTo("file-1");
                    assertThat(play.downloadUrl()).isEqualTo("https://storage.example/file-1");
                    assertThat(play.expiresAt()).isEqualTo(expiresAt);
                })
                .verifyComplete();
    }

    @Test
    void reportRecordsARealInteractionWithoutModerationResult() {
        var workId = UUID.randomUUID();
        var reportId = UUID.randomUUID();
        when(works.findById(workId)).thenReturn(Mono.just(work(
                workId, "bob", "workspace-b", "published", "public", NOW, 0, 0)));
        when(interactions.report(
                        eq(workId), eq("alice"), eq("workspace-a"), anyMap(), eq(NOW)))
                .thenReturn(Mono.just(reportId));

        StepVerifier.create(service.report(ALICE, workId, new WorkReportRequest("spam", "details")))
                .assertNext(result -> {
                    assertThat(result.reportId()).isEqualTo(reportId);
                    assertThat(result.status()).isEqualTo("recorded");
                    assertThat(result.recordedAt()).isEqualTo(NOW);
                })
                .verifyComplete();
    }

    @Test
    void remixCreatesAPrivateDraftAndIncrementsTheSourceChain() {
        var sourceId = UUID.randomUUID();
        var source = work(sourceId, "bob", "workspace-b", "published", "public", NOW, 0, 5);
        when(works.findById(sourceId)).thenReturn(Mono.just(source));
        when(works.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(interactions.incrementRemix(sourceId)).thenReturn(Mono.just(1L));

        StepVerifier.create(service.remix(ALICE, sourceId, new WorkRemixRequest(null, null)))
                .assertNext(result -> {
                    assertThat(result.work().ownerId()).isEqualTo("alice");
                    assertThat(result.work().workspaceId()).isEqualTo("workspace-a");
                    assertThat(result.work().remixedFromWorkId()).isEqualTo(sourceId);
                    assertThat(result.work().status()).isEqualTo(CreationValues.CONTENT_STATUS_DRAFT);
                    assertThat(result.work().visibility()).isEqualTo(CreationValues.VISIBILITY_PRIVATE);
                    assertThat(result.sourceRemixCount()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void remixTruncatesAnExplicitTitleToTheStoredMaximum() {
        var sourceId = UUID.randomUUID();
        when(works.findById(sourceId)).thenReturn(Mono.just(
                work(sourceId, "bob", "workspace-b", "published", "public", NOW, 0, 0)));
        when(works.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(interactions.incrementRemix(sourceId)).thenReturn(Mono.just(1L));
        var title = "x".repeat(600);

        StepVerifier.create(service.remix(ALICE, sourceId, new WorkRemixRequest(title, null)))
                .assertNext(result -> assertThat(result.work().title()).hasSize(512))
                .verifyComplete();
    }

    private static Asset asset(
            UUID id, String owner, String workspace, String storageFileId) {
        return new Asset(
                id,
                owner,
                workspace,
                "image",
                "creation",
                storageFileId,
                null,
                null,
                "asset",
                null,
                "prompt",
                Map.of(),
                "image/png",
                1L,
                1,
                1,
                null,
                List.of(),
                Map.of(),
                CreationValues.ASSET_STATUS_ACTIVE,
                NOW,
                NOW);
    }

    private static Work work(
            UUID id,
            String owner,
            String workspace,
            String status,
            String visibility,
            Instant publishedAt,
            long views,
            long likes) {
        return work(id, owner, workspace, status, visibility, publishedAt, views, likes, UUID.randomUUID());
    }

    private static Work work(
            UUID id,
            String owner,
            String workspace,
            String status,
            String visibility,
            Instant publishedAt,
            long views,
            long likes,
            UUID primaryAssetId) {
        return new Work(
                id,
                owner,
                workspace,
                "title",
                "description",
                null,
                primaryAssetId,
                null,
                null,
                null,
                List.of(),
                Map.of(),
                status,
                visibility,
                views,
                likes,
                2,
                3,
                4,
                publishedAt,
                NOW,
                NOW);
    }
}
