package cn.tolim.pudans.core.creation.content;

import cn.tolim.pudans.contract.creation.Asset;
import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.Work;
import cn.tolim.pudans.contract.creation.dto.ExploreFeed;
import cn.tolim.pudans.contract.creation.dto.WorkDetail;
import cn.tolim.pudans.contract.creation.dto.WorkInteractionResult;
import cn.tolim.pudans.contract.creation.dto.WorkPlay;
import cn.tolim.pudans.contract.creation.dto.WorkRelated;
import cn.tolim.pudans.contract.creation.dto.WorkRemixRequest;
import cn.tolim.pudans.contract.creation.dto.WorkRemixResult;
import cn.tolim.pudans.contract.creation.dto.WorkReportRequest;
import cn.tolim.pudans.contract.creation.dto.WorkReportResult;
import cn.tolim.pudans.contract.creation.dto.WorkShareResult;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.creation.AssetRepository;
import cn.tolim.pudans.infra.r2dbc.creation.CreationCursor;
import cn.tolim.pudans.infra.r2dbc.creation.InteractionRepository;
import cn.tolim.pudans.infra.r2dbc.creation.WorkRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class Works {

    private static final int RELATED_LIMIT = 12;

    private static final int TITLE_MAX_LENGTH = 512;

    private static final String WORK_NOT_FOUND = "work-not-found";

    private static final String EXPLORE_EMPTY_STATE = "暂无公开作品。";

    private static final String ALL_EMPTY_STATE = "暂无作品。";

    private final WorkRepository works;

    private final InteractionRepository interactions;

    private final AssetRepository assets;

    private final StorageTransfer storageTransfer;

    private Clock clock = Clock.systemUTC();

    public Mono<ExploreFeed> exploreFeed(
            RequestIdentity identity,
            String q,
            UUID templateId,
            UUID activityId,
            String creator,
            String cursor,
            Integer requestedLimit) {
        var limit = CreationCursor.limit(requestedLimit);
        var position = CreationCursor.decode(cursor, "explore-cursor-invalid");
        return works
                .feed(q, templateId, activityId, creator, position, limit + 1)
                .map(fetched -> {
                    var publicItems = fetched.stream()
                            .filter(work -> ContentVisibility.isPublicPublished(
                                    work.status(), work.visibility(), work.publishedAt()))
                            .toList();
                    var hasMore = publicItems.size() > limit;
                    var items = hasMore ? publicItems.subList(0, limit) : publicItems;
                    var nextCursor = hasMore && !items.isEmpty()
                            ? CreationCursor.encode(items.getLast().publishedAt(), items.getLast().id())
                            : null;
                    return new ExploreFeed(
                            List.copyOf(items),
                            nextCursor,
                            limit,
                            items.isEmpty() ? EXPLORE_EMPTY_STATE : null,
                            now());
                });
    }

    public Mono<ExploreFeed> allFeed(
            String q,
            UUID templateId,
            UUID activityId,
            String creator,
            String cursor,
            Integer requestedLimit) {
        var limit = CreationCursor.limit(requestedLimit);
        var position = CreationCursor.decode(cursor, "explore-cursor-invalid");
        return works
                .allFeed(q, templateId, activityId, creator, position, limit + 1)
                .map(fetched -> {
                    var hasMore = fetched.size() > limit;
                    var items = hasMore ? fetched.subList(0, limit) : fetched;
                    var nextCursor = hasMore && !items.isEmpty()
                            ? CreationCursor.encode(items.getLast().updatedAt(), items.getLast().id())
                            : null;
                    return new ExploreFeed(
                            List.copyOf(items),
                            nextCursor,
                            limit,
                            items.isEmpty() ? ALL_EMPTY_STATE : null,
                            now());
                });
    }

    public Mono<WorkDetail> view(RequestIdentity identity, UUID workId) {
        return requireVisible(identity, workId).flatMap(work -> {
            var viewCount = ContentVisibility.isPublicPublished(
                            work.status(), work.visibility(), work.publishedAt())
                    ? interactions.incrementView(work.id()).switchIfEmpty(Mono.just(work.viewCount()))
                    : Mono.just(work.viewCount());
            return Mono.zip(
                            viewCount,
                            interactions.exists(
                                    work.id(), identity.ownerId(), CreationValues.INTERACTION_LIKE),
                            interactions.exists(
                                    work.id(), identity.ownerId(), CreationValues.INTERACTION_FAVORITE),
                            interactions.isFollowing(identity.ownerId(), work.ownerId()))
                    .map(tuple -> new WorkDetail(
                            work,
                            tuple.getT1(),
                            work.likeCount(),
                            work.favoriteCount(),
                            work.shareCount(),
                            work.remixCount(),
                            tuple.getT2(),
                            tuple.getT3(),
                            tuple.getT4()));
        });
    }

    public Mono<WorkPlay> play(RequestIdentity identity, UUID workId) {
        return requireVisible(identity, workId).flatMap(work -> {
            if (work.primaryAssetId() == null) {
                return Mono.error(notReady());
            }
            return assets.findById(work.primaryAssetId()).flatMap(asset -> {
                if (!work.ownerId().equals(asset.ownerId())
                        || !work.workspaceId().equals(asset.workspaceId())
                        || !StringUtils.hasText(asset.storageFileId())) {
                    return Mono.error(notReady());
                }
                return storageTransfer
                        .downloadUrl(asset.storageFileId(), null)
                        .map(url -> new WorkPlay(
                                work.id().toString(),
                                asset.storageFileId(),
                                url.url(),
                                url.expiresAt()))
                        .onErrorMap(
                                ApiException.class,
                                error -> "storage-file-not-found".equals(error.getCode())
                                        ? notReady()
                                        : error);
            }).switchIfEmpty(Mono.error(notReady()));
        });
    }

    public Mono<WorkRelated> related(RequestIdentity identity, UUID workId) {
        return requireVisible(identity, workId)
                .flatMap(work -> works.related(work, RELATED_LIMIT))
                .map(items -> new WorkRelated(
                        items.stream()
                                .filter(work -> ContentVisibility.isPublicPublished(
                                        work.status(), work.visibility(), work.publishedAt()))
                                .toList(),
                        "暂无相关作品。"));
    }

    public Mono<WorkInteractionResult> like(
            RequestIdentity identity, UUID workId, Boolean active) {
        return toggle(identity, workId, active, CreationValues.INTERACTION_LIKE);
    }

    public Mono<WorkInteractionResult> favorite(
            RequestIdentity identity, UUID workId, Boolean active) {
        return toggle(identity, workId, active, CreationValues.INTERACTION_FAVORITE);
    }

    public Mono<WorkShareResult> share(RequestIdentity identity, UUID workId) {
        return requireVisible(identity, workId)
                .flatMap(work -> interactions.share(
                        work.id(), identity.ownerId(), identity.workspaceId(), now()))
                .switchIfEmpty(Mono.error(notFound()))
                .map(WorkShareResult::new);
    }

    public Mono<WorkReportResult> report(
            RequestIdentity identity, UUID workId, WorkReportRequest request) {
        if (request == null || !StringUtils.hasText(request.reason())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "work-report-reason-required", "举报原因不能为空。"));
        }
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("reason", request.reason().trim());
        if (StringUtils.hasText(request.details())) {
            metadata.put("details", request.details().trim());
        }
        var recordedAt = now();
        return requireVisible(identity, workId)
                .flatMap(work -> interactions.report(
                        work.id(), identity.ownerId(), identity.workspaceId(), metadata, recordedAt))
                .switchIfEmpty(Mono.error(notFound()))
                .map(reportId -> new WorkReportResult(reportId, "recorded", recordedAt));
    }

    public Mono<WorkRemixResult> remix(
            RequestIdentity identity, UUID workId, WorkRemixRequest request) {
        return requireVisible(identity, workId).flatMap(source -> {
            var now = now();
            var title = request != null && StringUtils.hasText(request.title())
                    ? truncate(request.title().trim())
                    : truncate(source.title() + " · Remix");
            var description = request == null || request.description() == null
                    ? source.description()
                    : request.description();
            var remix = new Work(
                    UUID.randomUUID(),
                    identity.ownerId(),
                    identity.workspaceId(),
                    title,
                    description,
                    source.coverAssetId(),
                    source.primaryAssetId(),
                    source.templateId(),
                    source.activityId(),
                    source.id(),
                    source.tags(),
                    source.metadata(),
                    CreationValues.CONTENT_STATUS_DRAFT,
                    CreationValues.VISIBILITY_PRIVATE,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    now,
                    now);
            return works.insert(remix).flatMap(created -> interactions
                    .incrementRemix(source.id())
                    .switchIfEmpty(Mono.just(source.remixCount() + 1))
                    .map(count -> new WorkRemixResult(created, count)));
        });
    }

    public Mono<Work> findById(UUID workId) {
        return works
                .findById(workId)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, WORK_NOT_FOUND, "作品不存在。")));
    }

    // 审核通过只改变审核状态，上架时才公开。
    public Mono<Work> approve(UUID workId) {
        return findById(workId).flatMap(work -> {
            if (CreationValues.CONTENT_STATUS_PUBLISHED.equals(work.status())) {
                return Mono.error(reviewConflict("work-already-published", "已上架作品不能重复审核。"));
            }
            return works.updateStatus(
                    work.id(),
                    CreationValues.CONTENT_STATUS_APPROVED,
                    work.visibility(),
                    null,
                    now());
        });
    }

    // 审核拒绝内容保持私有且不公开。
    public Mono<Work> reject(UUID workId) {
        return findById(workId).flatMap(work -> {
            if (CreationValues.CONTENT_STATUS_PUBLISHED.equals(work.status())) {
                return Mono.error(reviewConflict("work-already-published", "已上架作品不能审核拒绝。"));
            }
            return works.updateStatus(
                    work.id(),
                    CreationValues.CONTENT_STATUS_REJECTED,
                    CreationValues.VISIBILITY_PRIVATE,
                    null,
                    now());
        });
    }

    // 只有审核通过的作品允许上架。
    public Mono<Work> publish(UUID workId) {
        return findById(workId).flatMap(work -> {
            if (!CreationValues.CONTENT_STATUS_APPROVED.equals(work.status())) {
                return Mono.error(reviewConflict("work-not-approved", "只有审核通过的作品可以上架。"));
            }
            var now = now();
            return works.updateStatus(
                    work.id(),
                    CreationValues.CONTENT_STATUS_PUBLISHED,
                    CreationValues.VISIBILITY_PUBLIC,
                    now,
                    now);
        });
    }

    // 下架后保留审核通过状态，等待再次上架。
    public Mono<Work> unpublish(UUID workId) {
        return findById(workId).flatMap(work -> {
            if (!CreationValues.CONTENT_STATUS_PUBLISHED.equals(work.status())) {
                return Mono.error(reviewConflict("work-not-published", "只有已上架作品可以下架。"));
            }
            return works.updateStatus(
                    work.id(),
                    CreationValues.CONTENT_STATUS_APPROVED,
                    CreationValues.VISIBILITY_PRIVATE,
                    null,
                    now());
        });
    }

    private static ApiException reviewConflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public boolean isOwned(Work work, RequestIdentity identity) {
        return ContentVisibility.owns(work.ownerId(), work.workspaceId(), identity);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private Mono<Work> requireVisible(RequestIdentity identity, UUID workId) {
        return works
                .findById(workId)
                .filter(work -> ContentVisibility.isPublicPublished(
                                work.status(), work.visibility(), work.publishedAt())
                        || ContentVisibility.owns(work.ownerId(), work.workspaceId(), identity))
                .switchIfEmpty(Mono.error(notFound()));
    }

    private Mono<WorkInteractionResult> toggle(
            RequestIdentity identity, UUID workId, Boolean requestedActive, String interaction) {
        return requireVisible(identity, workId)
                .flatMap(work -> {
                    return interactions
                            .exists(work.id(), identity.ownerId(), interaction)
                            .flatMap(exists -> {
                                var desired = requestedActive == null ? !exists : requestedActive;
                                if (desired == exists) {
                                    return Mono.just(new InteractionRepository.InteractionCount(
                                            exists, count(work, interaction)));
                                }
                                return desired
                                        ? add(work.id(), identity, interaction)
                                        : remove(work.id(), identity, interaction);
                            });
                })
                .switchIfEmpty(Mono.error(notFound()))
                .map(count -> new WorkInteractionResult(
                        interaction, count.active(), count.count()));
    }

    private static long count(Work work, String interaction) {
        return CreationValues.INTERACTION_LIKE.equals(interaction)
                ? work.likeCount()
                : work.favoriteCount();
    }

    private Mono<InteractionRepository.InteractionCount> add(
            UUID workId, RequestIdentity identity, String interaction) {
        if (CreationValues.INTERACTION_LIKE.equals(interaction)) {
            return interactions.addLike(
                    workId, identity.ownerId(), identity.workspaceId(), now());
        }
        return interactions.addFavorite(
                workId, identity.ownerId(), identity.workspaceId(), now());
    }

    private Mono<InteractionRepository.InteractionCount> remove(
            UUID workId, RequestIdentity identity, String interaction) {
        if (CreationValues.INTERACTION_LIKE.equals(interaction)) {
            return interactions.removeLike(
                    workId, identity.ownerId(), identity.workspaceId(), now());
        }
        return interactions.removeFavorite(
                workId, identity.ownerId(), identity.workspaceId(), now());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, WORK_NOT_FOUND, "作品不存在。");
    }

    private static ApiException notReady() {
        return new ApiException(
                HttpStatus.CONFLICT, "work-play-not-ready", "作品暂无可播放的文件。");
    }

    private static String truncate(String value) {
        return value.length() <= TITLE_MAX_LENGTH ? value : value.substring(0, TITLE_MAX_LENGTH);
    }

    private Instant now() {
        return clock.instant();
    }
}
