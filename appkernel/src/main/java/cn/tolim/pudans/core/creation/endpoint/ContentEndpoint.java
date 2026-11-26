package cn.tolim.pudans.core.creation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.contract.creation.Template;
import cn.tolim.pudans.contract.creation.Work;
import cn.tolim.pudans.contract.creation.dto.WorkInteractionRequest;
import cn.tolim.pudans.contract.creation.dto.WorkRemixRequest;
import cn.tolim.pudans.contract.creation.dto.WorkReportRequest;
import cn.tolim.pudans.core.creation.archive.AssetCatalog;
import cn.tolim.pudans.core.creation.content.Templates;
import cn.tolim.pudans.core.creation.content.Works;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import java.util.LinkedHashMap;
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
public class ContentEndpoint implements CustomEndpoint {

    private static final String TAG = "CreationContentV1alpha1";

    private final Works works;

    private final Templates templates;

    private final AssetCatalog catalog;

    private final ReactiveRequestIdentity identity;

    private final CreationAudit audits;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/catalogs/-/explore", this::exploreCatalog, builder -> builder.operationId("getExploreCatalog")
                        .description("Get the catalog_entries-backed explore categories and filters.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Explore catalog")))
                .GET("/explore/-/feed", this::exploreFeed, builder -> builder.operationId("getExploreFeed")
                        .description("List published public works with cursor pagination.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Explore feed")))
                .GET("/works/-/all", this::allWorks, builder -> builder.operationId("listAllWorks")
                        .description("List all works with cursor pagination for administration.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Work feed")))
                .GET("/works/{name}/view", this::viewWork, builder -> builder.operationId("viewWork")
                        .description("View a public published work or a work owned by the current identity.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Work.class)))
                .POST("/works/{name}/approve", this::approveWork, builder -> builder.operationId("approveWork")
                        .description("Approve a work for publication.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Work.class)))
                .POST("/works/{name}/reject", this::rejectWork, builder -> builder.operationId("rejectWork")
                        .description("Reject a work during content review.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Work.class)))
                .POST("/works/{name}/publish", this::publishWork, builder -> builder.operationId("publishWork")
                        .description("Publish an approved work.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Work.class)))
                .POST("/works/{name}/unpublish", this::unpublishWork, builder -> builder.operationId("unpublishWork")
                        .description("Unpublish a published work.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Work.class)))
                .GET("/works/{name}/play", this::playWork, builder -> builder.operationId("playWork")
                        .description("Resolve a real storage download URL for the work primary asset.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Work playback URL")))
                .GET("/works/{name}/related", this::relatedWorks, builder -> builder.operationId("relatedWorks")
                        .description("List public works related by creator, template or activity.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Related works")))
                .POST("/works/{name}/-/like", this::likeWork, builder -> builder.operationId("likeWork")
                        .description("Set or toggle an idempotent work like.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Like state")))
                .POST("/works/{name}/-/favorite", this::favoriteWork, builder -> builder.operationId("favoriteWork")
                        .description("Set or toggle an idempotent work favorite.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Favorite state")))
                .POST("/works/{name}/-/share", this::shareWork, builder -> builder.operationId("shareWork")
                        .description("Record a work share and return the real share count.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Share count")))
                .POST("/works/{name}/-/report", this::reportWork, builder -> builder.operationId("reportWork")
                        .description("Record a work report without fabricating a moderation decision.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Recorded report")))
                .POST("/works/{name}/-/remix", this::remixWork, builder -> builder.operationId("remixWork")
                        .description("Create a private draft remixed from a visible work.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Remixed work")))
                .GET("/templates/-/feed", this::templateFeed, builder -> builder.operationId("getTemplateFeed")
                        .description("List published public templates with cursor pagination.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Template feed")))
                .GET("/templates/-/all", this::allTemplates, builder -> builder.operationId("listAllTemplates")
                        .description("List all templates with cursor pagination for administration.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Template feed")))
                .GET("/templates/{name}/view", this::viewTemplate, builder -> builder.operationId("viewTemplate")
                        .description("View a public published template or a template owned by the current identity.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Template view")))
                .POST("/templates/{name}/approve", this::approveTemplate, builder -> builder
                        .operationId("approveTemplate")
                        .description("Approve a template for publication.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Template.class)))
                .POST("/templates/{name}/reject", this::rejectTemplate, builder -> builder
                        .operationId("rejectTemplate")
                        .description("Reject a template during content review.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Template.class)))
                .POST("/templates/{name}/publish", this::publishTemplate, builder -> builder
                        .operationId("publishTemplate")
                        .description("Publish an approved template.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Template.class)))
                .POST("/templates/{name}/unpublish", this::unpublishTemplate, builder -> builder
                        .operationId("unpublishTemplate")
                        .description("Unpublish a published template.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(Template.class)))
                .build();
    }

    private Mono<ServerResponse> exploreCatalog(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(ignored -> catalog.explore())
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private Mono<ServerResponse> exploreFeed(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(owner -> works.exploreFeed(
                        owner,
                        query(request, "q"),
                        uuidQuery(request, "templateId", "explore-template-invalid"),
                        uuidQuery(request, "activityId", "explore-activity-invalid"),
                        query(request, "creator"),
                        query(request, "cursor"),
                        integerQuery(request, "limit")))
                .flatMap(feed -> ServerResponse.ok().bodyValue(feed));
    }

    private Mono<ServerResponse> allWorks(ServerRequest request) {
        return works
                .allFeed(
                        query(request, "q"),
                        uuidQuery(request, "templateId", "explore-template-invalid"),
                        uuidQuery(request, "activityId", "explore-activity-invalid"),
                        query(request, "creator"),
                        query(request, "cursor"),
                        integerQuery(request, "limit"))
                .flatMap(feed -> ServerResponse.ok().bodyValue(feed));
    }

    private Mono<ServerResponse> viewWork(ServerRequest request) {
        var workId = uuidPath(request, "work-not-found", "作品不存在。");
        return identity
                .resolve(request)
                .flatMap(owner -> works.view(owner, workId))
                .flatMap(detail -> ServerResponse.ok().bodyValue(detail));
    }

    private Mono<ServerResponse> approveWork(ServerRequest request) {
        return review(works.approve(uuidPath(request, "work-not-found", "作品不存在。")));
    }

    private Mono<ServerResponse> rejectWork(ServerRequest request) {
        return review(works.reject(uuidPath(request, "work-not-found", "作品不存在。")));
    }

    private Mono<ServerResponse> publishWork(ServerRequest request) {
        return review(works.publish(uuidPath(request, "work-not-found", "作品不存在。")));
    }

    private Mono<ServerResponse> unpublishWork(ServerRequest request) {
        return review(works.unpublish(uuidPath(request, "work-not-found", "作品不存在。")));
    }

    private Mono<ServerResponse> playWork(ServerRequest request) {
        var workId = uuidPath(request, "work-not-found", "作品不存在。");
        return identity
                .resolve(request)
                .flatMap(owner -> works.play(owner, workId))
                .flatMap(play -> ServerResponse.ok().bodyValue(play));
    }

    private Mono<ServerResponse> relatedWorks(ServerRequest request) {
        var workId = uuidPath(request, "work-not-found", "作品不存在。");
        return identity
                .resolve(request)
                .flatMap(owner -> works.related(owner, workId))
                .flatMap(related -> ServerResponse.ok().bodyValue(related));
    }

    private Mono<ServerResponse> likeWork(ServerRequest request) {
        var workId = uuidPath(request, "work-not-found", "作品不存在。");
        return identity.resolve(request).flatMap(owner -> interactionBody(request)
                .flatMap(body -> works.like(owner, workId, body.active())))
                .flatMap(result -> auditInteraction(request, result.interaction(), result.active(), result.count())
                        .thenReturn(result))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> favoriteWork(ServerRequest request) {
        var workId = uuidPath(request, "work-not-found", "作品不存在。");
        return identity.resolve(request).flatMap(owner -> interactionBody(request)
                .flatMap(body -> works.favorite(owner, workId, body.active())))
                .flatMap(result -> auditInteraction(request, result.interaction(), result.active(), result.count())
                        .thenReturn(result))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> shareWork(ServerRequest request) {
        var workId = uuidPath(request, "work-not-found", "作品不存在。");
        return identity
                .resolve(request)
                .flatMap(owner -> works.share(owner, workId))
                .flatMap(result -> auditInteraction(request, "share", true, result.shareCount())
                        .thenReturn(result))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> reportWork(ServerRequest request) {
        var workId = uuidPath(request, "work-not-found", "作品不存在。");
        return identity.resolve(request).flatMap(owner -> request
                .bodyToMono(WorkReportRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> works.report(owner, workId, body)))
                .flatMap(result -> auditInteraction(request, "report", true, null)
                        .thenReturn(result))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> remixWork(ServerRequest request) {
        var workId = uuidPath(request, "work-not-found", "作品不存在。");
        return identity.resolve(request).flatMap(owner -> request
                .bodyToMono(WorkRemixRequest.class)
                .defaultIfEmpty(new WorkRemixRequest(null, null))
                .flatMap(body -> works.remix(owner, workId, body)))
                .flatMap(result -> audits
                        .record(
                                request,
                                "REMIX_WORK",
                                "Work",
                                result.work().id().toString(),
                                Map.of("remixedFromWorkId", workId.toString()))
                        .thenReturn(result))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> templateFeed(ServerRequest request) {
        return identity
                .resolve(request)
                .flatMap(owner -> templates.feed(
                        owner,
                        query(request, "q"),
                        query(request, "cursor"),
                        integerQuery(request, "limit")))
                .flatMap(feed -> ServerResponse.ok().bodyValue(feed));
    }

    private Mono<ServerResponse> allTemplates(ServerRequest request) {
        return templates
                .allFeed(query(request, "q"), query(request, "cursor"), integerQuery(request, "limit"))
                .flatMap(feed -> ServerResponse.ok().bodyValue(feed));
    }

    private Mono<ServerResponse> viewTemplate(ServerRequest request) {
        var templateId = uuidPath(request, "template-not-found", "模板不存在。");
        return identity
                .resolve(request)
                .flatMap(owner -> templates.view(owner, templateId))
                .flatMap(template -> ServerResponse.ok().bodyValue(template));
    }

    private Mono<ServerResponse> approveTemplate(ServerRequest request) {
        return review(templates.approve(uuidPath(request, "template-not-found", "模板不存在。")));
    }

    private Mono<ServerResponse> rejectTemplate(ServerRequest request) {
        return review(templates.reject(uuidPath(request, "template-not-found", "模板不存在。")));
    }

    private Mono<ServerResponse> publishTemplate(ServerRequest request) {
        return review(templates.publish(uuidPath(request, "template-not-found", "模板不存在。")));
    }

    private Mono<ServerResponse> unpublishTemplate(ServerRequest request) {
        return review(templates.unpublish(uuidPath(request, "template-not-found", "模板不存在。")));
    }

    private static Mono<ServerResponse> review(Mono<?> result) {
        return result.flatMap(value -> ServerResponse.ok().bodyValue(value));
    }

    private Mono<WorkInteractionRequest> interactionBody(ServerRequest request) {
        return request
                .bodyToMono(WorkInteractionRequest.class)
                .defaultIfEmpty(new WorkInteractionRequest(null));
    }

    private Mono<Void> auditInteraction(
            ServerRequest request, String interaction, boolean active, Long count) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("interaction", interaction);
        snapshot.put("active", active);
        if (count != null) {
            snapshot.put("count", count);
        }
        return audits.record(
                request,
                "WORK_" + interaction.toUpperCase(),
                "Work",
                request.pathVariable("name"),
                snapshot);
    }

    private static UUID uuidPath(ServerRequest request, String errorCode, String message) {
        try {
            return UUID.fromString(request.pathVariable("name"));
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.NOT_FOUND, errorCode, message);
        }
    }

    private static UUID uuidQuery(ServerRequest request, String name, String errorCode) {
        var value = query(request, name);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, "查询参数不是有效标识。");
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
                    HttpStatus.BAD_REQUEST, "content-limit-invalid", "分页数量不是有效整数。");
        }
    }
}
