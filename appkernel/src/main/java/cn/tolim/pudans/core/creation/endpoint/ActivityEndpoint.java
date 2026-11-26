package cn.tolim.pudans.core.creation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.contract.creation.Activity;
import cn.tolim.pudans.contract.creation.dto.ActivitySubmissionRequest;
import cn.tolim.pudans.core.creation.content.Activities;
import cn.tolim.pudans.core.creation.content.Creators;
import cn.tolim.pudans.core.creation.content.Subjects;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ActivityEndpoint implements CustomEndpoint {

    private static final String TAG = "ActivityV1alpha1";

    private final Activities activities;

    private final Subjects subjects;

    private final Creators creators;

    private final ReactiveRequestIdentity identity;

    private final CreationAudit audits;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/activities/-/feed", this::activityFeed, builder -> builder.operationId("getActivityFeed")
                        .description("List published public activities with cursor pagination.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Activity feed")))
                .GET("/activities/-/all", this::allActivityFeed, builder -> builder.operationId("listAllActivities")
                        .description("List all activities with cursor pagination for administration.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Activity feed")))
                .GET("/activities/{name}/view", this::viewActivity, builder -> builder.operationId("viewActivity")
                        .description("View a public published activity or an owned activity.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Activity view")))
                .POST("/activities/{name}/approve", this::approveActivity, builder -> builder
                        .operationId("approveActivity")
                        .description("Approve an activity for publication.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Activity.class)))
                .POST("/activities/{name}/reject", this::rejectActivity, builder -> builder
                        .operationId("rejectActivity")
                        .description("Reject an activity during content review.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Activity.class)))
                .POST("/activities/{name}/publish", this::publishActivity, builder -> builder
                        .operationId("publishActivity")
                        .description("Publish an approved activity.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Activity.class)))
                .POST("/activities/{name}/unpublish", this::unpublishActivity, builder -> builder
                        .operationId("unpublishActivity")
                        .description("Unpublish a published activity.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Activity.class)))
                .POST(
                        "/activities/{name}/submissions",
                        this::submitActivity,
                        builder -> builder.operationId("submitActivity")
                                .description("Submit an owned work to a visible activity.")
                                .tag(TAG)
                                .response(responseBuilder().responseCode("200").description("Activity submission")))
                .GET(
                        "/activities/{name}/submissions/-/mine",
                        this::mySubmissions,
                        builder -> builder.operationId("getMyActivitySubmissions")
                                .description("List the current identity activity submissions.")
                                .tag(TAG)
                                .response(responseBuilder().responseCode("200").description("Activity submissions")))
                .GET("/subjects/-/mine", this::mySubjects, builder -> builder.operationId("getMySubjects")
                        .description("List subjects owned by the current identity and workspace.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Subject page")))
                .GET("/subjects/-/all", this::allSubjects, builder -> builder.operationId("listAllSubjects")
                        .description("List subjects across the platform for administration.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Subject page")))
                .GET("/subjects/{name}/view", this::viewSubject, builder -> builder.operationId("viewSubject")
                        .description("View a public subject or an owned subject.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Subject view")))
                .POST("/creators/{name}/-/follow", this::followCreator, builder -> builder.operationId("followCreator")
                        .description("Follow a creator idempotently and return the real follower count.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Creator follow state")))
                .build();
    }

    private Mono<ServerResponse> activityFeed(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(owner -> activities.feed(
                        owner,
                        query(request, "q"),
                        query(request, "cursor"),
                        integerQuery(request, "limit")))
                .flatMap(feed -> ServerResponse.ok().bodyValue(feed));
    }

    private Mono<ServerResponse> allActivityFeed(ServerRequest request) {
        return activities
                .allFeed(query(request, "q"), query(request, "cursor"), integerQuery(request, "limit"))
                .flatMap(feed -> ServerResponse.ok().bodyValue(feed));
    }

    private Mono<ServerResponse> viewActivity(ServerRequest request) {
        var activityId = uuidPath(request, "activity-not-found");
        return identity
                .resolve(request)
                .flatMap(owner -> activities.view(owner, activityId))
                .flatMap(activity -> ServerResponse.ok().bodyValue(activity));
    }

    private Mono<ServerResponse> approveActivity(ServerRequest request) {
        return review(activities.approve(uuidPath(request, "activity-not-found")));
    }

    private Mono<ServerResponse> rejectActivity(ServerRequest request) {
        return review(activities.reject(uuidPath(request, "activity-not-found")));
    }

    private Mono<ServerResponse> publishActivity(ServerRequest request) {
        return review(activities.publish(uuidPath(request, "activity-not-found")));
    }

    private Mono<ServerResponse> unpublishActivity(ServerRequest request) {
        return review(activities.unpublish(uuidPath(request, "activity-not-found")));
    }

    private static Mono<ServerResponse> review(Mono<?> result) {
        return result.flatMap(value -> ServerResponse.ok().bodyValue(value));
    }

    private Mono<ServerResponse> submitActivity(ServerRequest request) {
        var activityId = uuidPath(request, "activity-not-found");
        return identity.resolve(request).flatMap(owner -> request
                .bodyToMono(ActivitySubmissionRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> activities.submit(owner, activityId, body)))
                .flatMap(submission -> audits
                        .record(
                                request,
                                "SUBMIT_ACTIVITY",
                                "ActivitySubmission",
                                submission.id().toString(),
                                Map.of(
                                        "activityId", submission.activityId().toString(),
                                        "workId", submission.workId().toString()))
                        .thenReturn(submission))
                .flatMap(submission -> ServerResponse.ok().bodyValue(submission));
    }

    private Mono<ServerResponse> mySubmissions(ServerRequest request) {
        var activityId = uuidPath(request, "activity-not-found");
        return identity
                .resolve(request)
                .flatMap(owner -> activities.mine(owner, activityId))
                .flatMap(page -> ServerResponse.ok().bodyValue(page));
    }

    private Mono<ServerResponse> mySubjects(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(owner -> subjects.mine(
                        owner, query(request, "cursor"), integerQuery(request, "limit")))
                .flatMap(page -> ServerResponse.ok().bodyValue(page));
    }

    private Mono<ServerResponse> allSubjects(ServerRequest request) {
        return subjects
                .all(query(request, "cursor"), integerQuery(request, "limit"))
                .flatMap(page -> ServerResponse.ok().bodyValue(page));
    }

    private Mono<ServerResponse> viewSubject(ServerRequest request) {
        var subjectId = uuidPath(request, "subject-not-found");
        return identity
                .resolve(request)
                .flatMap(owner -> subjects.view(owner, subjectId))
                .flatMap(subject -> ServerResponse.ok().bodyValue(subject));
    }

    private Mono<ServerResponse> followCreator(ServerRequest request) {
        var creatorId = request.pathVariable("name");
        return identity
                .resolve(request)
                .flatMap(owner -> creators.follow(owner, creatorId))
                .flatMap(result -> audits
                        .record(
                                request,
                                "FOLLOW_CREATOR",
                                "CreatorFollow",
                                creatorId,
                                Map.of("followerCount", result.followerCount()))
                        .thenReturn(result))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private static UUID uuidPath(ServerRequest request, String errorCode) {
        try {
            return UUID.fromString(request.pathVariable("name"));
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.NOT_FOUND, errorCode, "资源不存在。");
        }
    }

    private static String query(ServerRequest request, String name) {
        var value = request.queryParam(name).orElse(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Integer integerQuery(ServerRequest request, String name) {
        var raw = request.queryParam(name).orElse(null);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "activity-limit-invalid", "分页数量不是有效整数。");
        }
    }
}
