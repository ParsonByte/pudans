package cn.tolim.pudans.core.ai.rag.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.ai.resource.KnowledgeBase;
import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.contract.extension.ListResult;
import cn.tolim.pudans.core.ai.rag.DocumentStatus;
import cn.tolim.pudans.core.ai.rag.IngestionResult;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseCodes;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseDocument;
import cn.tolim.pudans.core.ai.rag.KnowledgeBases;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseQueryLog;
import cn.tolim.pudans.core.ai.rag.KnowledgeBaseRetrievalResult;
import cn.tolim.pudans.core.ai.rag.RetrievalOptions;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.exception.UserNotFoundException;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.fn.builders.parameter.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class KnowledgeBaseEndpoint implements CustomEndpoint {

    private static final String TAG = "KnowledgeBaseV1alpha1";

    private static final String CODE_SEARCH_INVALID = "ai-kb-search-invalid";

    private static final String CODE_AUTH_REQUIRED = "ai-kb-auth-required";

    private static final int DEFAULT_RRF_K = 60;

    private static final int MAX_RRF_K = 1000;

    private static final double DEFAULT_SCORE_THRESHOLD = 0.3;

    private static final int DEFAULT_TOP_K = 8;

    private static final int MAX_TOP_K = 50;

    private static final int DEFAULT_CHAR_BUDGET = 4000;

    private static final int MAX_CHAR_BUDGET = 100_000;

    private static final int DEFAULT_PAGE = 1;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 100;

    private static final int DEFAULT_QUERY_LOG_LIMIT = 50;

    private static final int MAX_QUERY_LOG_LIMIT = 200;

    private final KnowledgeBases knowledgeBases;

    private final Accounts accounts;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/knowledgebases", this::list, builder -> builder
                        .operationId("listKnowledgeBases")
                        .description("List the knowledge bases readable by the current user.")
                        .tag(TAG)
                        .response(responseBuilder()
                                .implementation(ListResult.generateGenericClass(KnowledgeBase.class))))
                .GET("/knowledgebases/-/search", this::search, builder -> builder
                        .operationId("searchKnowledgeBases")
                        .description("Search the knowledge bases readable by the current user.")
                        .tag(TAG)
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("q")
                                .description("Search query.")
                                .required(true))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("kbCodes")
                                .description("Comma-separated knowledge base codes."))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("topK")
                                .description("Maximum number of returned chunks."))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("scoreThreshold")
                                .description("Minimum fused score between 0 and 1."))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("rrfK")
                                .description("Reciprocal rank fusion constant."))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("charBudget")
                                .description("Maximum prompt characters."))
                        .response(responseBuilder().implementation(KnowledgeBaseRetrievalResult.class)))
                .GET("/knowledgebases/{name}", this::detail, builder -> builder
                        .operationId("getKnowledgeBaseDetail")
                        .description("Get a readable knowledge base by name.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the knowledge base."))
                        .response(responseBuilder().implementation(KnowledgeBase.class)))
                .GET("/knowledgebases/{name}/documents", this::listDocuments, builder -> builder
                        .operationId("listKnowledgeBaseDocuments")
                        .description("List documents of a readable knowledge base.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the knowledge base."))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("page")
                                .description("Page number, starts from 1."))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("size")
                                .description("Page size between 1 and 100."))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("status")
                                .description("Document status filter."))
                        .response(responseBuilder()
                                .implementation(ListResult.generateGenericClass(KnowledgeBaseDocument.class))))
                .POST("/knowledgebases/{name}/documents", this::uploadDocument, builder -> builder
                        .operationId("addKnowledgeBaseDocument")
                        .description("Bind a stored file to a readable knowledge base and ingest it.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the knowledge base."))
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .implementation(DocumentUploadRequest.class))
                        .response(responseBuilder().implementation(IngestionResult.class)))
                .GET("/knowledgebases/{name}/documents/{docId}", this::documentDetail, builder -> builder
                        .operationId("getKnowledgeBaseDocument")
                        .description("Get a document of a readable knowledge base.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the knowledge base."))
                        .parameter(pathParameter("docId", "Document identifier."))
                        .response(responseBuilder().implementation(KnowledgeBaseDocument.class)))
                .DELETE("/knowledgebases/{name}/documents/{docId}", this::deleteDocument, builder -> builder
                        .operationId("deleteKnowledgeBaseDocument")
                        .description("Delete a document and its chunks from a readable knowledge base.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the knowledge base."))
                        .parameter(pathParameter("docId", "Document identifier."))
                        .response(responseBuilder().responseCode("204").description("Document deleted.")))
                .POST(
                        "/knowledgebases/{name}/documents/{docId}/retry",
                        this::retryDocument,
                        builder -> builder
                                .operationId("retryKnowledgeBaseDocument")
                                .description("Re-ingest a failed document of a readable knowledge base.")
                                .tag(TAG)
                                .parameter(pathParameter("name", "metadata.name of the knowledge base."))
                                .parameter(pathParameter("docId", "Document identifier."))
                                .response(responseBuilder().implementation(KnowledgeBaseDocument.class)))
                .POST("/knowledgebases/{name}/reindex", this::reindex, builder -> builder
                        .operationId("reindexKnowledgeBase")
                        .description("Re-ingest the ready documents of a readable knowledge base.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the knowledge base."))
                        .response(responseBuilder().responseCode("202").implementation(ReindexResponse.class)))
                .GET("/knowledgebases/{name}/querylogs", this::queryLogs, builder -> builder
                        .operationId("listKnowledgeBaseQueryLogs")
                        .description("List recent retrieval logs of a readable knowledge base.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the knowledge base."))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("limit")
                                .description("Maximum number of returned logs."))
                        .response(responseBuilder().implementationArray(KnowledgeBaseQueryLog.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return context().flatMap(context -> knowledgeBases
                .listVisible(context.uid(), context.roles(), null)
                .collectList()
                .flatMap(items -> ServerResponse.ok().bodyValue(new ListResult<KnowledgeBase>(items))));
    }

    private Mono<ServerResponse> detail(ServerRequest request) {
        return context().flatMap(context -> knowledgeBases
                .findVisible(request.pathVariable("name"), context.uid(), context.roles(), null)
                .flatMap(knowledgeBase -> ServerResponse.ok().bodyValue(knowledgeBase)));
    }

    private Mono<ServerResponse> search(ServerRequest request) {
        return Mono.defer(() -> {
            var query = request.queryParam("q")
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .orElseThrow(() -> searchInvalid("检索问题不能为空。"));
            var options = new RetrievalOptions(
                    intQuery(request, "rrfK", DEFAULT_RRF_K, 1, MAX_RRF_K),
                    scoreThreshold(request),
                    intQuery(request, "topK", DEFAULT_TOP_K, 1, MAX_TOP_K),
                    intQuery(request, "charBudget", DEFAULT_CHAR_BUDGET, 1, MAX_CHAR_BUDGET));
            var kbCodes = kbCodes(request);
            return context().flatMap(context -> knowledgeBases
                    .search(context.uid(), context.roles(), null, query, kbCodes, options)
                    .flatMap(result -> ServerResponse.ok().bodyValue(result)));
        });
    }

    private Mono<ServerResponse> listDocuments(ServerRequest request) {
        var name = request.pathVariable("name");
        var page = intQuery(request, "page", DEFAULT_PAGE, 1, Integer.MAX_VALUE);
        var size = intQuery(request, "size", DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);
        var status = documentStatus(request);
        return context().flatMap(context -> knowledgeBases
                .findReadable(name, context.uid(), context.roles(), null)
                .flatMap(knowledgeBase -> knowledgeBases.listDocuments(
                        KnowledgeBaseCodes.codeOf(knowledgeBase), status, page, size))
                .flatMap(result -> ServerResponse.ok().bodyValue(result)));
    }

    private Mono<ServerResponse> uploadDocument(ServerRequest request) {
        var name = request.pathVariable("name");
        return context().flatMap(context -> request.bodyToMono(DocumentUploadRequest.class)
                .switchIfEmpty(Mono.error(invalidDocument("请求体不能为空。")))
                .flatMap(body -> knowledgeBases
                        .findReadable(name, context.uid(), context.roles(), null)
                        .flatMap(knowledgeBase -> knowledgeBases.uploadDocument(
                                knowledgeBase, context.uid(), body.storageFileId(), body.fileName()))
                        .flatMap(result -> ServerResponse.ok().bodyValue(result))));
    }

    private Mono<ServerResponse> documentDetail(ServerRequest request) {
        var name = request.pathVariable("name");
        var documentId = documentId(request);
        return context().flatMap(context -> knowledgeBases
                .findReadable(name, context.uid(), context.roles(), null)
                .flatMap(knowledgeBase -> knowledgeBases.findDocument(
                        KnowledgeBaseCodes.codeOf(knowledgeBase), documentId))
                .flatMap(document -> ServerResponse.ok().bodyValue(document)));
    }

    private Mono<ServerResponse> deleteDocument(ServerRequest request) {
        var name = request.pathVariable("name");
        var documentId = documentId(request);
        return context().flatMap(context -> knowledgeBases
                .findReadable(name, context.uid(), context.roles(), null)
                .flatMap(knowledgeBase -> knowledgeBases.deleteDocument(
                        KnowledgeBaseCodes.codeOf(knowledgeBase), documentId))
                .then(ServerResponse.noContent().build()));
    }

    private Mono<ServerResponse> retryDocument(ServerRequest request) {
        var name = request.pathVariable("name");
        var documentId = documentId(request);
        return context().flatMap(context -> knowledgeBases
                .findReadable(name, context.uid(), context.roles(), null)
                .flatMap(knowledgeBase -> knowledgeBases.retryDocument(
                        knowledgeBase, documentId, context.uid()))
                .flatMap(document -> ServerResponse.ok().bodyValue(document)));
    }

    private Mono<ServerResponse> reindex(ServerRequest request) {
        var name = request.pathVariable("name");
        return context().flatMap(context -> knowledgeBases
                .findReadable(name, context.uid(), context.roles(), null)
                .flatMap(knowledgeBase -> knowledgeBases.reindex(knowledgeBase, context.uid()))
                .flatMap(count -> ServerResponse.accepted().bodyValue(new ReindexResponse(count))));
    }

    private Mono<ServerResponse> queryLogs(ServerRequest request) {
        var name = request.pathVariable("name");
        var limit = intQuery(request, "limit", DEFAULT_QUERY_LOG_LIMIT, 1, MAX_QUERY_LOG_LIMIT);
        return context().flatMap(context -> knowledgeBases
                .findReadable(name, context.uid(), context.roles(), null)
                .flatMap(knowledgeBase -> knowledgeBases
                        .listQueryLogs(KnowledgeBaseCodes.codeOf(knowledgeBase), limit)
                        .collectList())
                .flatMap(logs -> ServerResponse.ok().bodyValue(logs)));
    }

    private Mono<UserContext> context() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .switchIfEmpty(Mono.error(unauthorized()))
                .flatMap(authentication -> accounts.getUser(authentication.getName())
                        .map(user -> new UserContext(
                                user.getSpec().getUid(),
                                AuthorityUtils.authoritiesToRoles(authentication.getAuthorities())))
                        .switchIfEmpty(Mono.error(unauthorized())))
                .onErrorMap(UserNotFoundException.class, error -> unauthorized());
    }

    private static List<String> kbCodes(ServerRequest request) {
        var values = request.queryParams().get("kbCodes");
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private static int intQuery(ServerRequest request, String name, int defaultValue, int min, int max) {
        var raw = request.queryParam(name).filter(StringUtils::hasText);
        if (raw.isEmpty()) {
            return defaultValue;
        }
        try {
            var value = Integer.parseInt(raw.get().trim());
            if (value < min || value > max) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException error) {
            throw new ServerWebInputException("参数 " + name + " 无效。");
        }
    }

    private static double scoreThreshold(ServerRequest request) {
        var raw = request.queryParam("scoreThreshold").filter(StringUtils::hasText);
        if (raw.isEmpty()) {
            return DEFAULT_SCORE_THRESHOLD;
        }
        try {
            var value = Double.parseDouble(raw.get().trim());
            if (!Double.isFinite(value) || value < 0 || value > 1) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException error) {
            throw new ServerWebInputException("参数 scoreThreshold 无效。");
        }
    }

    private static @Nullable DocumentStatus documentStatus(ServerRequest request) {
        var raw = request.queryParam("status").filter(StringUtils::hasText);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return DocumentStatus.valueOf(raw.get().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new ServerWebInputException("参数 status 无效。");
        }
    }

    private static long documentId(ServerRequest request) {
        try {
            var id = Long.parseLong(request.pathVariable("docId"));
            if (id < 1) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    KnowledgeBases.CODE_DOCUMENT_INVALID,
                    "文档标识无效。");
        }
    }

    private static Builder pathParameter(String name, String description) {
        return parameterBuilder()
                .in(ParameterIn.PATH)
                .name(name)
                .description(description)
                .required(true);
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, CODE_AUTH_REQUIRED, "缺少有效的认证信息。");
    }

    private static ApiException searchInvalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_SEARCH_INVALID, message);
    }

    private static ApiException invalidDocument(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST, KnowledgeBases.CODE_DOCUMENT_INVALID, message);
    }

    public record DocumentUploadRequest(String storageFileId, String fileName) {}

    public record ReindexResponse(long queued) {}

    private record UserContext(String uid, Set<String> roles) {}
}
