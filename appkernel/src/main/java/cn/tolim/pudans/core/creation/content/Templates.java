package cn.tolim.pudans.core.creation.content;

import cn.tolim.pudans.contract.creation.CreationValues;
import cn.tolim.pudans.contract.creation.Template;
import cn.tolim.pudans.contract.creation.dto.TemplateFeed;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.creation.CreationCursor;
import cn.tolim.pudans.infra.r2dbc.creation.TemplateRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class Templates {

    private static final String EMPTY_STATE = "暂无公开模板。";

    private static final String ALL_EMPTY_STATE = "暂无模板。";

    private final TemplateRepository templates;

    private Clock clock = Clock.systemUTC();

    public Mono<TemplateFeed> feed(
            RequestIdentity identity, String q, String cursor, Integer requestedLimit) {
        var limit = CreationCursor.limit(requestedLimit);
        var position = CreationCursor.decode(cursor, "template-cursor-invalid");
        return templates
                .feed(q, position, limit + 1)
                .map(fetched -> {
                    var publicItems = fetched.stream()
                            .filter(template -> ContentVisibility.isPublicPublished(
                                    template.status(),
                                    template.visibility(),
                                    template.publishedAt()))
                            .toList();
                    var hasMore = publicItems.size() > limit;
                    var items = hasMore ? publicItems.subList(0, limit) : publicItems;
                    var nextCursor = hasMore && !items.isEmpty()
                            ? CreationCursor.encode(items.getLast().publishedAt(), items.getLast().id())
                            : null;
                    return new TemplateFeed(
                            List.copyOf(items),
                            nextCursor,
                            limit,
                            items.isEmpty() ? EMPTY_STATE : null,
                            now());
                });
    }

    public Mono<TemplateFeed> allFeed(
            String q, String cursor, Integer requestedLimit) {
        var limit = CreationCursor.limit(requestedLimit);
        var position = CreationCursor.decode(cursor, "template-cursor-invalid");
        return templates
                .allFeed(q, position, limit + 1)
                .map(fetched -> {
                    var hasMore = fetched.size() > limit;
                    var items = hasMore ? fetched.subList(0, limit) : fetched;
                    var nextCursor = hasMore && !items.isEmpty()
                            ? CreationCursor.encode(items.getLast().updatedAt(), items.getLast().id())
                            : null;
                    return new TemplateFeed(
                            List.copyOf(items),
                            nextCursor,
                            limit,
                            items.isEmpty() ? ALL_EMPTY_STATE : null,
                            now());
                });
    }

    public Mono<Template> view(RequestIdentity identity, UUID templateId) {
        return templates
                .findById(templateId)
                .filter(template -> ContentVisibility.isPublicPublished(
                                template.status(),
                                template.visibility(),
                                template.publishedAt())
                        || ContentVisibility.owns(
                                template.ownerId(), template.workspaceId(), identity))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "template-not-found", "模板不存在。")));
    }

    // 审核通过只改变审核状态，上架时才公开。
    public Mono<Template> approve(UUID templateId) {
        return require(templateId).flatMap(template -> {
            if (CreationValues.CONTENT_STATUS_PUBLISHED.equals(template.status())) {
                return Mono.error(
                        reviewConflict("template-already-published", "已上架模板不能重复审核。"));
            }
            return templates.updateStatus(
                    template.id(),
                    CreationValues.CONTENT_STATUS_APPROVED,
                    template.visibility(),
                    null,
                    now());
        });
    }

    // 审核拒绝模板保持私有且不公开。
    public Mono<Template> reject(UUID templateId) {
        return require(templateId).flatMap(template -> {
            if (CreationValues.CONTENT_STATUS_PUBLISHED.equals(template.status())) {
                return Mono.error(
                        reviewConflict("template-already-published", "已上架模板不能审核拒绝。"));
            }
            return templates.updateStatus(
                    template.id(),
                    CreationValues.CONTENT_STATUS_REJECTED,
                    CreationValues.VISIBILITY_PRIVATE,
                    null,
                    now());
        });
    }

    // 只有审核通过的模板允许上架。
    public Mono<Template> publish(UUID templateId) {
        return require(templateId).flatMap(template -> {
            if (!CreationValues.CONTENT_STATUS_APPROVED.equals(template.status())) {
                return Mono.error(reviewConflict("template-not-approved", "只有审核通过的模板可以上架。"));
            }
            var now = now();
            return templates.updateStatus(
                    template.id(),
                    CreationValues.CONTENT_STATUS_PUBLISHED,
                    CreationValues.VISIBILITY_PUBLIC,
                    now,
                    now);
        });
    }

    // 下架后保留审核通过状态，等待再次上架。
    public Mono<Template> unpublish(UUID templateId) {
        return require(templateId).flatMap(template -> {
            if (!CreationValues.CONTENT_STATUS_PUBLISHED.equals(template.status())) {
                return Mono.error(reviewConflict("template-not-published", "只有已上架模板可以下架。"));
            }
            return templates.updateStatus(
                    template.id(),
                    CreationValues.CONTENT_STATUS_APPROVED,
                    CreationValues.VISIBILITY_PRIVATE,
                    null,
                    now());
        });
    }

    private Mono<Template> require(UUID templateId) {
        return templates
                .findById(templateId)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "template-not-found", "模板不存在。")));
    }

    private static ApiException reviewConflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private Instant now() {
        return clock.instant();
    }
}
