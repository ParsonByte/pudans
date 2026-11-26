package cn.tolim.pudans.core.creation.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.creation.Activity;
import cn.tolim.pudans.contract.creation.ActivitySubmission;
import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.Subject;
import cn.tolim.pudans.contract.creation.dto.ActivityFeed;
import cn.tolim.pudans.contract.creation.dto.ActivitySubmissionPage;
import cn.tolim.pudans.contract.creation.dto.ActivitySubmissionRequest;
import cn.tolim.pudans.contract.creation.dto.CreatorFollowResult;
import cn.tolim.pudans.contract.creation.dto.SubjectPage;
import cn.tolim.pudans.core.creation.content.Activities;
import cn.tolim.pudans.core.creation.content.Creators;
import cn.tolim.pudans.core.creation.content.Subjects;
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
class ActivityEndpointTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");

    private static final RequestIdentity ALICE = new RequestIdentity("alice", "workspace-a");

    @Mock
    Activities activities;

    @Mock
    Subjects subjects;

    @Mock
    Creators creators;

    @Mock
    ReactiveRequestIdentity identity;

    @Mock
    CreationAudit audits;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new ActivityEndpoint(activities, subjects, creators, identity, audits);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void listsEverySubjectForAdministrativeInspection() {
        when(subjects.all(null, null))
                .thenReturn(Mono.just(new SubjectPage(List.of(), null, 20, "暂无主体。", NOW)));

        client.get()
                .uri("/subjects/-/all")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items")
                .isArray();
    }

    @Test
    void listsEveryActivityForAdministrativeInspection() {
        when(activities.allFeed(null, null, null))
                .thenReturn(Mono.just(new ActivityFeed(List.of(), null, 20, "暂无活动。", NOW)));

        client.get()
                .uri("/activities/-/all")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items")
                .isArray()
                .jsonPath("$.emptyState")
                .isEqualTo("暂无活动。");
    }

    @Test
    void approvesAnActivityForAdministrativeReview() {
        var activity = new Activity(
                UUID.randomUUID(),
                "bob",
                "workspace-b",
                "活动",
                "描述",
                null,
                null,
                null,
                CreationValues.CONTENT_STATUS_DRAFT,
                CreationValues.VISIBILITY_PRIVATE,
                null,
                null,
                NOW,
                NOW);
        when(activities.approve(activity.id())).thenReturn(Mono.just(activity));

        client.post()
                .uri("/activities/{id}/approve", activity.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(activity.id().toString());
    }

    @Test
    void returnsAnEmptyActivityFeedWithoutPublishedActivities() {
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(activities.feed(ALICE, null, null, null))
                .thenReturn(Mono.just(new ActivityFeed(List.of(), null, 20, "暂无公开活动。", NOW)));

        client.get()
                .uri("/activities/-/feed")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items")
                .isArray()
                .jsonPath("$.emptyState")
                .isEqualTo("暂无公开活动。");
    }

    @Test
    void submitsAnOwnedWorkAndRecordsAnAudit() {
        var activityId = UUID.randomUUID();
        var workId = UUID.randomUUID();
        var submission = new ActivitySubmission(
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
        var request = new ActivitySubmissionRequest(workId);
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(activities.submit(ALICE, activityId, request)).thenReturn(Mono.just(submission));
        when(audits.record(
                        any(),
                        eq("SUBMIT_ACTIVITY"),
                        eq("ActivitySubmission"),
                        eq(submission.id().toString()),
                        anyMap()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/activities/{id}/submissions", activityId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.workId")
                .isEqualTo(workId.toString())
                .jsonPath("$.status")
                .isEqualTo("submitted");
    }

    @Test
    void listsOnlyTheCurrentOwnersActivitySubmissions() {
        var activityId = UUID.randomUUID();
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(activities.mine(ALICE, activityId)).thenReturn(Mono.just(
                new ActivitySubmissionPage(List.of(), "暂无提交记录。")));

        client.get()
                .uri("/activities/{id}/submissions/-/mine", activityId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items")
                .isArray()
                .jsonPath("$.emptyState")
                .isEqualTo("暂无提交记录。");
    }

    @Test
    void listsOnlyTheCurrentOwnersSubjects() {
        var subject = subject(UUID.randomUUID());
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(subjects.mine(ALICE, null, null))
                .thenReturn(Mono.just(new SubjectPage(List.of(subject), null, 20, null, NOW)));

        client.get()
                .uri("/subjects/-/mine")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].id")
                .isEqualTo(subject.id().toString());
    }

    @Test
    void followsACreatorAndRecordsAnAudit() {
        var creatorId = "bob";
        when(identity.resolve(any())).thenReturn(Mono.just(ALICE));
        when(creators.follow(ALICE, creatorId))
                .thenReturn(Mono.just(new CreatorFollowResult(true, 12)));
        when(audits.record(
                        any(),
                        eq("FOLLOW_CREATOR"),
                        eq("CreatorFollow"),
                        eq(creatorId),
                        anyMap()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/creators/{id}/-/follow", creatorId)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.following")
                .isEqualTo(true)
                .jsonPath("$.followerCount")
                .isEqualTo(12);
    }

    private static Subject subject(UUID id) {
        return new Subject(
                id,
                "alice",
                "workspace-a",
                "subject",
                "description",
                "character",
                null,
                List.of(),
                Map.of(),
                "active",
                CreationValues.VISIBILITY_PRIVATE,
                NOW,
                NOW);
    }
}
