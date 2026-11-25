package cn.tolim.pudans.core.storage.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.storage.StorageRoute;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.storage.Policies;
import cn.tolim.pudans.core.storage.StorageRoutes;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.driver.StorageTestResult;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageRouteEndpoint implements CustomEndpoint {

    private static final String TAG = "StorageRouteV1alpha1";

    private final StorageRoutes storageRoutes;

    private final StorageTransfer storageTransfer;

    private final Policies policies;

    private final AuditLog auditLog;

    // 注册路由查询、写入、删除、连通性测试与用途目录。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/storageroutes", this::list, builder -> builder.operationId("listStorageRoutes")
                        .description("List configured storage purpose routes.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Storage routes")))
                .GET(
                        "/storageroutes/-/purposes",
                        this::purposes,
                        builder -> builder.operationId("listStorageRoutePurposes")
                                .description("List supported storage route purposes and fallback behaviour.")
                                .tag(TAG)
                                .response(responseBuilder().responseCode("200").description("Supported purposes")))
                .GET("/storageroutes/{purpose}", this::get, builder -> builder.operationId("getStorageRoute")
                        .description("Get the storage route configured for a purpose.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(StorageRoute.class)))
                .PUT("/storageroutes/{purpose}", this::put, builder -> builder.operationId("putStorageRoute")
                        .description("Create or replace the storage route for a purpose.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(org.springdoc.core.fn.builders.content.Builder.contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(org.springdoc.core.fn.builders.schema.Builder.schemaBuilder()
                                                .implementation(StorageRoute.Spec.class))))
                        .response(responseBuilder().implementation(StorageRoute.class)))
                .DELETE("/storageroutes/{purpose}", this::delete, builder -> builder.operationId("deleteStorageRoute")
                        .description("Delete a storage route without deleting stored files.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("204").description("Storage route deleted")))
                .POST("/storageroutes/{purpose}/-/test", this::test, builder -> builder.operationId("testStorageRoute")
                        .description("Test the ready policy resolved for a storage route purpose.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(StorageTestResult.class)))
                .build();
    }

    // 返回全部存储路由配置。
    private Mono<ServerResponse> list(ServerRequest request) {
        return storageRoutes
                .list()
                .collectList()
                .flatMap(routes -> ServerResponse.ok().bodyValue(routes));
    }

    // 返回首期支持的用途、规范名称与默认策略回退行为。
    private Mono<ServerResponse> purposes(ServerRequest request) {
        var result = List.of(StorageRoute.Purpose.values()).stream()
                .map(PurposeDescriptor::of)
                .toList();
        return ServerResponse.ok().bodyValue(result);
    }

    // 按用途返回已配置的路由。
    private Mono<ServerResponse> get(ServerRequest request) {
        return storageRoutes
                .get(purpose(request))
                .flatMap(route -> ServerResponse.ok().bodyValue(route));
    }

    // 创建或替换用途绑定并记录审计。
    private Mono<ServerResponse> put(ServerRequest request) {
        var purpose = purpose(request);
        return request.bodyToMono(StorageRoute.Spec.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(spec -> storageRoutes.put(purpose, spec))
                .flatMap(route -> recordAudit(
                                request,
                                "PUT_STORAGE_ROUTE",
                                route.getMetadata().getName(),
                                "SUCCESS",
                                routeSnapshot(route))
                        .thenReturn(route))
                .flatMap(route -> ServerResponse.ok().bodyValue(route));
    }

    // 删除用途绑定并记录审计，不触碰已有对象。
    private Mono<ServerResponse> delete(ServerRequest request) {
        var purpose = purpose(request);
        return storageRoutes
                .delete(purpose)
                .flatMap(route -> recordAudit(
                                request,
                                "DELETE_STORAGE_ROUTE",
                                route.getMetadata().getName(),
                                "SUCCESS",
                                routeSnapshot(route))
                        .thenReturn(route))
                .then(ServerResponse.noContent().build());
    }

    // 解析就绪策略后执行连通性测试并记录结果。
    private Mono<ServerResponse> test(ServerRequest request) {
        var purpose = purpose(request);
        return storageRoutes
                .resolve(purpose)
                .flatMap(policy -> {
                    var policyName = policy.getMetadata().getName();
                    return storageTransfer
                            .test(policyName)
                            .flatMap(result -> policies
                                    .recordTestResult(policyName, result)
                                    .thenReturn(result))
                            .flatMap(result -> recordTestAudit(request, purpose, policyName, result)
                                    .thenReturn(result));
                })
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    // 从路径解析受支持的用途，拒绝未登记值。
    private static StorageRoute.Purpose purpose(ServerRequest request) {
        try {
            return StorageRoute.Purpose.fromCode(request.pathVariable("purpose"));
        } catch (IllegalArgumentException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "storage-route-purpose-invalid",
                    error.getMessage());
        }
    }

    // 记录路由变更审计，失败不影响主操作。
    private Mono<Void> recordAudit(
            ServerRequest request,
            String operation,
            String name,
            String result,
            Map<String, Object> snapshot) {
        return auditLog
                .recordChild(
                        request.exchange(),
                        null,
                        operation,
                        "StorageRoute",
                        name,
                        result,
                        snapshot,
                        null)
                .onErrorResume(error -> {
                    log.warn("记录存储路由审计失败：{}", error.getMessage());
                    return Mono.empty();
                });
    }

    // 记录连通性测试审计，只保留用途、策略名与结果摘要。
    private Mono<Void> recordTestAudit(
            ServerRequest request,
            StorageRoute.Purpose purpose,
            String policyName,
            StorageTestResult result) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("purpose", purpose.getCode());
        snapshot.put("policyName", policyName);
        snapshot.put("success", result.success());
        snapshot.put("message", result.message());
        snapshot.put("testedAt", result.testedAt());
        return recordAudit(
                request,
                "TEST_STORAGE_ROUTE",
                purpose.routeName(),
                result.success() ? "SUCCESS" : "FAILURE",
                snapshot);
    }

    // 组装不含任何凭据的路由审计快照。
    private static Map<String, Object> routeSnapshot(StorageRoute route) {
        var snapshot = new LinkedHashMap<String, Object>();
        var spec = route.getSpec();
        snapshot.put("purpose", spec == null || spec.getPurpose() == null ? null : spec.getPurpose().getCode());
        snapshot.put("policyName", spec == null ? null : spec.getPolicyName());
        snapshot.put("enabled", spec != null && spec.isEnabled());
        return snapshot;
    }

    public record PurposeDescriptor(
            String purpose,
            String name,
            boolean fallbackToDefaultPolicy) {

        // 根据枚举值生成管理端用途目录项。
        public static PurposeDescriptor of(StorageRoute.Purpose purpose) {
            return new PurposeDescriptor(
                    purpose.getCode(),
                    purpose.routeName(),
                    purpose.fallbackToDefaultPolicy());
        }
    }
}
