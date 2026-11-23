package cn.tolim.pudans.extension.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.extension.SecretDirectory;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
import cn.tolim.pudans.extension.contract.SecretCatalog;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SecretCatalogEndpoint implements CustomEndpoint {

    private static final String TAG = "SecretV1alpha1";

    private static final int DEFAULT_PAGE = 1;

    private static final int DEFAULT_SIZE = 20;

    private final SecretDirectory secretDirectory;

    // 注册密钥安全目录查询路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/secrets/catalog", this::catalog, builder -> builder
                        .operationId("getSecretCatalog")
                        .description("Browse secret metadata without exposing stored values.")
                        .tag(TAG)
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("page")
                                .required(false)
                                .description("One-based page number; defaults to 1.")
                                .implementation(Integer.class))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("size")
                                .required(false)
                                .description("Page size between 1 and 100; defaults to 20.")
                                .implementation(Integer.class))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("keyword")
                                .required(false)
                                .description("Case-insensitive match on the secret name or any key.")
                                .implementation(String.class))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("type")
                                .required(false)
                                .description("Exact secret type filter.")
                                .implementation(String.class))
                        .response(responseBuilder().implementation(SecretCatalog.class)))
                .build();
    }

    // 固定挂载到平台 API 版本。
    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }

    // 校验分页参数后查询目录。
    private Mono<ServerResponse> catalog(ServerRequest request) {
        var page = intParam(request, "page", DEFAULT_PAGE);
        var size = intParam(request, "size", DEFAULT_SIZE);
        if (page < 1) {
            return Mono.error(new ServerWebInputException("查询参数 'page' 必须大于或等于 1。"));
        }
        if (size < 1 || size > SecretDirectory.MAX_PAGE_SIZE) {
            return Mono.error(new ServerWebInputException("查询参数 'size' 必须在 1 到 100 之间。"));
        }
        var keyword = request.queryParam("keyword").filter(StringUtils::hasText).orElse(null);
        var type = request.queryParam("type").filter(StringUtils::hasText).orElse(null);
        return secretDirectory
                .catalog(keyword, type, page, size)
                .flatMap(catalog -> ServerResponse.ok().bodyValue(catalog));
    }

    // 解析整数参数，空值使用默认值。
    private static int intParam(ServerRequest request, String name, int defaultValue) {
        return request.queryParam(name)
                .filter(StringUtils::hasText)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new ServerWebInputException("查询参数 '" + name + "' 必须是整数。");
                    }
                })
                .orElse(defaultValue);
    }
}
