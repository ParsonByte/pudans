package cn.tolim.pudans.core.storage.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.core.storage.OneDriveOAuth;
import cn.tolim.pudans.core.storage.Policies;
import cn.tolim.pudans.core.storage.StoragePolicyValidator;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.driver.StorageDriverFactoryRegistry;
import cn.tolim.pudans.core.storage.driver.StorageTestResult;
import cn.tolim.pudans.infra.exception.ApiException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class StoragePolicyEndpoint implements CustomEndpoint {

    private final StoragePolicyValidator validator;

    private final Policies policies;

    private final StorageTransfer storageTransfer;

    private final StorageDriverFactoryRegistry registry;

    private final OneDriveOAuth oneDriveOAuth;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "StoragePolicyV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/storagepolicies/-/types", this::types, builder -> builder.operationId("listStoragePolicyTypes")
                        .description("List supported storage policy types.")
                        .tag(tag)
                        .response(responseBuilder().responseCode("200").description("Supported types")))
                .POST("/storagepolicies/-/validate", this::validate, builder -> builder.operationId("validateStoragePolicy")
                        .description("Validate a storage policy without persisting it.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(org.springdoc.core.fn.builders.content.Builder.contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(org.springdoc.core.fn.builders.schema.Builder.schemaBuilder()
                                                .implementation(StoragePolicy.class))))
                        .response(responseBuilder().responseCode("200").description("Validation result")))
                .POST("/storagepolicies/{name}/test", this::test, builder -> builder.operationId("testStoragePolicy")
                        .description("Test the connection of a storage policy.")
                        .tag(tag)
                        .response(responseBuilder().implementation(StorageTestResult.class)))
                .POST("/storagepolicies/{name}/cors", this::cors, builder -> builder.operationId("configureStoragePolicyCors")
                        .description("Configure CORS for a storage policy.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(org.springdoc.core.fn.builders.content.Builder.contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(org.springdoc.core.fn.builders.schema.Builder.schemaBuilder()
                                                .implementation(CorsRequest.class))))
                        .response(responseBuilder().responseCode("204").description("CORS configured")))
                .GET("/storagepolicies/{name}/oauth/authorize", this::oauthAuthorize, builder -> builder.operationId("authorizeStoragePolicyOAuth")
                        .description("Build the OneDrive authorization URL for a storage policy.")
                        .tag(tag)
                        .response(responseBuilder().implementation(OneDriveOAuth.OAuthUrl.class)))
                .GET("/storagepolicies/-/oauth/callback", this::oauthCallback, builder -> builder.operationId("callbackStoragePolicyOAuth")
                        .description("Complete the OneDrive authorization code flow.")
                        .tag(tag)
                        .response(responseBuilder().implementation(OneDriveOAuth.OAuthStatus.class)))
                .GET("/storagepolicies/{name}/oauth/status", this::oauthStatus, builder -> builder.operationId("getStoragePolicyOAuthStatus")
                        .description("Get the OneDrive authorization status of a storage policy.")
                        .tag(tag)
                        .response(responseBuilder().implementation(OneDriveOAuth.OAuthStatus.class)))
                .GET("/storagepolicies/{name}/oauth/root", this::oauthRoot, builder -> builder.operationId("resolveStoragePolicyOAuthRoot")
                        .description("Resolve a SharePoint site URL to a OneDrive drive ID.")
                        .tag(tag)
                        .response(responseBuilder().responseCode("200").description("Resolved drive ID")))
                .build();
    }

    private Mono<ServerResponse> types(ServerRequest request) {
        return ServerResponse.ok().bodyValue(registry.descriptors());
    }

    private Mono<ServerResponse> validate(ServerRequest request) {
        return request.bodyToMono(StoragePolicy.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(policy -> ServerResponse.ok().bodyValue(validator.validatePolicy(policy)));
    }

    private Mono<ServerResponse> test(ServerRequest request) {
        var name = request.pathVariable("name");
        return storageTransfer
                .test(name)
                .flatMap(result -> policies.recordTestResult(name, result).thenReturn(result))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> cors(ServerRequest request) {
        var name = request.pathVariable("name");
        return request.bodyToMono(CorsRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> storageTransfer.configureCors(name, body.origins()))
                .then(ServerResponse.status(HttpStatus.NO_CONTENT).build());
    }

    private Mono<ServerResponse> oauthAuthorize(ServerRequest request) {
        return oneDriveOAuth
                .authorizeUrl(request.pathVariable("name"))
                .flatMap(url -> ServerResponse.ok().bodyValue(url));
    }

    private Mono<ServerResponse> oauthCallback(ServerRequest request) {
        var error = request.queryParam("error").filter(StringUtils::hasText);
        if (error.isPresent()) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "storage-onedrive-oauth-denied",
                    "OneDrive 授权被拒绝：" + error.get() + "。"));
        }
        var code = request.queryParam("code").filter(StringUtils::hasText)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "storage-onedrive-code-missing", "缺少 OneDrive 授权码。"));
        var state = request.queryParam("state").filter(StringUtils::hasText)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "storage-onedrive-state-missing", "缺少 OneDrive 授权状态。"));
        return oneDriveOAuth.callback(code, state).flatMap(status -> ServerResponse.ok().bodyValue(status));
    }

    private Mono<ServerResponse> oauthStatus(ServerRequest request) {
        return oneDriveOAuth
                .status(request.pathVariable("name"))
                .flatMap(status -> ServerResponse.ok().bodyValue(status));
    }

    private Mono<ServerResponse> oauthRoot(ServerRequest request) {
        var url = request.queryParam("url").filter(StringUtils::hasText)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "storage-onedrive-site-required", "SharePoint 站点地址不能为空。"));
        return oneDriveOAuth
                .resolveRoot(request.pathVariable("name"), url)
                .map(driveId -> java.util.Map.of("driveId", driveId))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    public record CorsRequest(List<String> origins) {}
}
