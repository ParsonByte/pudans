package cn.tolim.pudans.core.creation.content;

import cn.tolim.pudans.contract.creation.Activity;
import cn.tolim.pudans.contract.creation.ActivitySubmission;
import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.dto.ActivityFeed;
import cn.tolim.pudans.contract.creation.dto.ActivitySubmissionPage;
import cn.tolim.pudans.contract.creation.dto.ActivitySubmissionRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.creation.ActivityRepository;
import cn.tolim.pudans.infra.r2dbc.creation.ActivitySubmissionRepository;
import cn.tolim.pudans.infra.r2dbc.creation.CreationCursor;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class Activities {

    private static final String EMPTY_STATE = "暂无公开活动。";

    private static final String ALL_EMPTY_STATE = "暂无活动。";

    private static final String SUBMISSION_EMPTY_STATE = "暂无提交记录。";

    private final ActivityRepository activities;

    private final ActivitySubmissionRepository submissions;

    private final Works works;

    private Clock clock = Clock.systemUTC();

    public Mono<ActivityFeed> feed(
            RequestIdentity identity, String q, String cursor, Integer requestedLimit) {
        var limit = CreationCursor.limit(requestedLimit);
        var position = CreationCursor.decode(cursor, "activity-cursor-invalid");
        return activities
                .feed(q, position, limit + 1)
                .map(fetched -> {
                    var publicItems = fetched.stream()
                            .filter(activity -> ContentVisibility.isPublicPublished(
                                    activity.status(), activity.visibility()))
                            .toList();
                    var hasMore = publicItems.size() > limit;
                    var items = hasMore ? publicItems.subList(0, limit) : publicItems;
                    var nextCursor = hasMore && !items.isEmpty()
                            ? CreationCursor.encode(sortAt(items.getLast()), items.getLast().id())
                            : null;
                    return new ActivityFeed(
                            List.copyOf(items),
                            nextCursor,
                            limit,
                            items.isEmpty() ? EMPTY_STATE : null,
                            now());
                });
    }

    public Mono<ActivityFeed> allFeed(String q, String cursor, Integer requestedLimit) {
        var limit = CreationCursor.limit(requestedLimit);
        var position = CreationCursor.decode(cursor, "activity-cursor-invalid");
        return activities
                .allFeed(q, position, limit + 1)
                .map(fetched -> {
                    var hasMore = fetched.size() > limit;
                    var items = hasMore ? fetched.subList(0, limit) : fetched;
                    var nextCursor = hasMore && !items.isEmpty()
                            ? CreationCursor.encode(items.getLast().updatedAt(), items.getLast().id())
                            : null;
                    return new ActivityFeed(
                            List.copyOf(items),
                            nextCursor,
                            limit,
                            items.isEmpty() ? ALL_EMPTY_STATE : null,
                            now());
                });
    }

    public Mono<Activity> view(RequestIdentity identity, UUID activityId) {
        return requireVisible(identity, activityId);
    }

    // 审核通过只改变审核状态，上架时才公开。
    public Mono<Activity> approve(UUID activityId) {
        return require(activityId).flatMap(activity -> {
            if (CreationValues.CONTENT_STATUS_PUBLISHED.equals(activity.status())) {
                return Mono.error(
                        reviewConflict("activity-already-published", "已上架活动不能重复审核。"));
            }
            return activities.updateStatus(
                    activity.id(),
                    CreationValues.CONTENT_STATUS_APPROVED,
                    activity.visibility(),
                    now());
        });
    }

    // 审核拒绝活动保持私有且不公开。
    public Mono<Activity> reject(UUID activityId) {
        return require(activityId).flatMap(activity -> {
            if (CreationValues.CONTENT_STATUS_PUBLISHED.equals(activity.status())) {
                return Mono.error(
                        reviewConflict("activity-already-published", "已上架活动不能审核拒绝。"));
            }
            return activities.updateStatus(
                    activity.id(),
                    CreationValues.CONTENT_STATUS_REJECTED,
                    CreationValues.VISIBILITY_PRIVATE,
                    now());
        });
    }

    // 只有审核通过的活动允许上架。
    public Mono<Activity> publish(UUID activityId) {
        return require(activityId).flatMap(activity -> {
            if (!CreationValues.CONTENT_STATUS_APPROVED.equals(activity.status())) {
                return Mono.error(reviewConflict("activity-not-approved", "只有审核通过的活动可以上架。"));
            }
            return activities.updateStatus(
                    activity.id(),
                    CreationValues.CONTENT_STATUS_PUBLISHED,
                    CreationValues.VISIBILITY_PUBLIC,
                    now());
        });
    }

    // 下架后保留审核通过状态，等待再次上架。
    public Mono<Activity> unpublish(UUID activityId) {
        return require(activityId).flatMap(activity -> {
            if (!CreationValues.CONTENT_STATUS_PUBLISHED.equals(activity.status())) {
                return Mono.error(reviewConflict("activity-not-published", "只有已上架活动可以下架。"));
            }
            return activities.updateStatus(
                    activity.id(),
                    CreationValues.CONTENT_STATUS_APPROVED,
                    CreationValues.VISIBILITY_PRIVATE,
                    now());
        });
    }

    private Mono<Activity> require(UUID activityId) {
        return activities
                .findById(activityId)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "activity-not-found", "活动不存在。")));
    }

    private static ApiException reviewConflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public Mono<ActivitySubmission> submit(
            RequestIdentity identity, UUID activityId, ActivitySubmissionRequest request) {
        if (request == null || request.workId() == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "activity-work-required", "提交作品不能为空。"));
        }
        return requireVisible(identity, activityId)
                .flatMap(activity -> works.findById(request.workId()).flatMap(work -> {
                    if (!works.isOwned(work, identity)) {
                        return Mono.error(new ApiException(
                                HttpStatus.NOT_FOUND, "work-not-found", "作品不存在。"));
                    }
                    return submissions
                            .find(activity.id(), work.id())
                            .switchIfEmpty(Mono.defer(() -> insertSubmission(identity, activity, work.id())));
                }))
                .onErrorResume(DuplicateKeyException.class, ignored -> submissions.find(
                        activityId, request.workId()))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.CONFLICT, "activity-submission-conflict", "活动提交发生并发冲突。")));
    }

    public Mono<ActivitySubmissionPage> mine(RequestIdentity identity, UUID activityId) {
        return requireVisible(identity, activityId)
                .flatMap(activity -> submissions.listMine(
                        activity.id(), identity.ownerId(), identity.workspaceId()))
                .map(items -> new ActivitySubmissionPage(
                        items, items.isEmpty() ? SUBMISSION_EMPTY_STATE : null));
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private Mono<Activity> requireVisible(RequestIdentity identity, UUID activityId) {
        return activities
                .findById(activityId)
                .filter(activity -> ContentVisibility.isPublicPublished(
                                activity.status(), activity.visibility())
                        || ContentVisibility.owns(
                                activity.ownerId(), activity.workspaceId(), identity))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "activity-not-found", "活动不存在。")));
    }

    private Mono<ActivitySubmission> insertSubmission(
            RequestIdentity identity, Activity activity, UUID workId) {
        var now = now();
        return submissions.insert(new ActivitySubmission(
                UUID.randomUUID(),
                activity.id(),
                workId,
                identity.ownerId(),
                identity.workspaceId(),
                CreationValues.SUBMISSION_STATUS_SUBMITTED,
                null,
                now,
                null,
                now,
                now));
    }

    private static Instant sortAt(Activity activity) {
        return activity.startsAt() == null ? activity.createdAt() : activity.startsAt();
    }

    private Instant now() {
        return clock.instant();
    }
}
