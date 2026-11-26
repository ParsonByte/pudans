package cn.tolim.pudans.core.creation.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.Activity;
import cn.tolim.pudans.contract.creation.ActivitySubmission;
import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.Work;
import cn.tolim.pudans.contract.creation.dto.ActivitySubmissionRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.creation.ActivityRepository;
import cn.tolim.pudans.infra.r2dbc.creation.ActivitySubmissionRepository;
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
class ActivitiesTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final RequestIdentity ALICE = new RequestIdentity("alice", "workspace-a");

    @Mock
    ActivityRepository activities;

    @Mock
    ActivitySubmissionRepository submissions;

    @Mock
    Works works;

    private Activities service;

    @BeforeEach
    void setUp() {
        service = new Activities(activities, submissions, works);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void feedKeepsOnlyPublishedPublicActivities() {
        var draft = activity(UUID.randomUUID(), "bob", "draft", "public");
        var published = activity(UUID.randomUUID(), "bob", "published", "public");
        when(activities.feed(isNull(), isNull(), eq(21))).thenReturn(Mono.just(List.of(draft, published)));

        StepVerifier.create(service.feed(ALICE, null, null, null))
                .assertNext(feed -> {
                    assertThat(feed.items()).extracting(Activity::id).containsExactly(published.id());
                    assertThat(feed.emptyState()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void allFeedIncludesEveryStatusAndVisibility() {
        var draft = activity(UUID.randomUUID(), "bob", "draft", "private");
        var published = activity(UUID.randomUUID(), "bob", "published", "public");
        when(activities.allFeed(isNull(), isNull(), eq(21)))
                .thenReturn(Mono.just(List.of(draft, published)));

        StepVerifier.create(service.allFeed(null, null, null))
                .assertNext(feed -> {
                    assertThat(feed.items()).extracting(Activity::id)
                            .containsExactly(draft.id(), published.id());
                    assertThat(feed.emptyState()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void emptyActivityFeedReturnsAnEmptyState() {
        when(activities.feed(isNull(), isNull(), eq(21))).thenReturn(Mono.just(List.of()));

        StepVerifier.create(service.feed(ALICE, null, null, null))
                .assertNext(feed -> {
                    assertThat(feed.items()).isEmpty();
                    assertThat(feed.emptyState()).isNotBlank();
                })
                .verifyComplete();
    }

    @Test
    void approvesAnActivityForReview() {
        var activityId = UUID.randomUUID();
        var draft = activity(activityId, "bob", "draft", "private");
        var approved = activity(activityId, "bob", "approved", "private");
        when(activities.findById(activityId)).thenReturn(Mono.just(draft));
        when(activities.updateStatus(activityId, "approved", "private", NOW))
                .thenReturn(Mono.just(approved));

        StepVerifier.create(service.approve(activityId))
                .assertNext(result -> assertThat(result.status()).isEqualTo("approved"))
                .verifyComplete();
    }

    @Test
    void rejectsPublishingAnActivityThatHasNotBeenApproved() {
        var activityId = UUID.randomUUID();
        when(activities.findById(activityId))
                .thenReturn(Mono.just(activity(activityId, "bob", "draft", "private")));

        StepVerifier.create(service.publish(activityId))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(409);
                            assertThat(api.getCode()).isEqualTo("activity-not-approved");
                        }))
                .verify();
    }

    @Test
    void cannotSubmitAWorkOwnedByAnotherUser() {
        var activityId = UUID.randomUUID();
        var workId = UUID.randomUUID();
        when(activities.findById(activityId))
                .thenReturn(Mono.just(activity(activityId, "bob", "published", "public")));
        when(works.findById(workId)).thenReturn(Mono.just(work(workId, "bob", "workspace-a")));
        when(works.isOwned(org.mockito.ArgumentMatchers.any(), eq(ALICE))).thenReturn(false);

        StepVerifier.create(service.submit(ALICE, activityId, new ActivitySubmissionRequest(workId)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, api -> {
                            assertThat(api.getStatusCode().value()).isEqualTo(404);
                            assertThat(api.getCode()).isEqualTo("work-not-found");
                        }))
                .verify();
    }

    @Test
    void emptySubmissionListHasAnEmptyState() {
        var activityId = UUID.randomUUID();
        when(activities.findById(activityId))
                .thenReturn(Mono.just(activity(activityId, "bob", "published", "public")));
        when(submissions.listMine(activityId, "alice", "workspace-a")).thenReturn(Mono.just(List.of()));

        StepVerifier.create(service.mine(ALICE, activityId))
                .assertNext(page -> {
                    assertThat(page.items()).isEmpty();
                    assertThat(page.emptyState()).isNotBlank();
                })
                .verifyComplete();
    }

    @Test
    void submissionStatusComesFromTheRealSubmittedState() {
        var activityId = UUID.randomUUID();
        var workId = UUID.randomUUID();
        var activity = activity(activityId, "bob", "published", "public");
        var work = work(workId, "alice", "workspace-a");
        when(activities.findById(activityId)).thenReturn(Mono.just(activity));
        when(works.findById(workId)).thenReturn(Mono.just(work));
        when(works.isOwned(work, ALICE)).thenReturn(true);
        when(submissions.find(activityId, workId)).thenReturn(Mono.empty());
        when(submissions.insert(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.submit(ALICE, activityId, new ActivitySubmissionRequest(workId)))
                .assertNext(submission -> {
                    assertThat(submission.activityId()).isEqualTo(activityId);
                    assertThat(submission.workId()).isEqualTo(workId);
                    assertThat(submission.ownerId()).isEqualTo("alice");
                    assertThat(submission.status()).isEqualTo(CreationValues.SUBMISSION_STATUS_SUBMITTED);
                    assertThat(submission.submittedAt()).isEqualTo(NOW);
                })
                .verifyComplete();
    }

    @Test
    void returnsAnExistingSubmissionWithoutInsertingItAgain() {
        var activityId = UUID.randomUUID();
        var workId = UUID.randomUUID();
        var existing = new ActivitySubmission(
                UUID.randomUUID(),
                activityId,
                workId,
                "alice",
                "workspace-a",
                CreationValues.SUBMISSION_STATUS_SUBMITTED,
                null,
                NOW,
                null,
                NOW,
                NOW);
        var activity = activity(activityId, "bob", "published", "public");
        var work = work(workId, "alice", "workspace-a");
        when(activities.findById(activityId)).thenReturn(Mono.just(activity));
        when(works.findById(workId)).thenReturn(Mono.just(work));
        when(works.isOwned(work, ALICE)).thenReturn(true);
        when(submissions.find(activityId, workId)).thenReturn(Mono.just(existing));

        StepVerifier.create(service.submit(ALICE, activityId, new ActivitySubmissionRequest(workId)))
                .assertNext(submission -> assertThat(submission.id()).isEqualTo(existing.id()))
                .verifyComplete();

        verify(submissions, never()).insert(any());
    }

    private static Activity activity(UUID id, String owner, String status, String visibility) {
        return new Activity(
                id,
                owner,
                "workspace-a",
                "activity",
                "description",
                "rules",
                null,
                null,
                status,
                visibility,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200),
                NOW,
                NOW);
    }

    private static Work work(UUID id, String owner, String workspace) {
        return new Work(
                id,
                owner,
                workspace,
                "title",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                Map.of(),
                CreationValues.CONTENT_STATUS_PUBLISHED,
                CreationValues.VISIBILITY_PUBLIC,
                0,
                0,
                0,
                0,
                0,
                NOW,
                NOW,
                NOW);
    }
}
