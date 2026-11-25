package cn.tolim.pudans.core.creation.content;

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

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private Instant now() {
        return clock.instant();
    }
}
