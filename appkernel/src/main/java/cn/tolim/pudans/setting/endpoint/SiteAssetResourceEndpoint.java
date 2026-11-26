package cn.tolim.pudans.setting.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;

import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.setting.SiteAssets;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SiteAssetResourceEndpoint implements CustomEndpoint {

    private static final MediaType SVG = MediaType.parseMediaType("image/svg+xml");

    private static final String SVG_CONTENT_SECURITY_POLICY = "default-src 'none'; style-src 'unsafe-inline'; sandbox";

    private final SiteAssets siteAssets;

    // 注册站点图片资源访问路由
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "SiteAssetV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/site-assets/{filename}", this::serve, builder -> builder
                        .operationId("GetSiteAsset")
                        .description("Get a stored site image asset.")
                        .tag(tag)
                        .response(responseBuilder()
                                .content(contentBuilder().mediaType(MediaType.IMAGE_PNG_VALUE))
                                .implementation(Resource.class)))
                .build();
    }

    // 读取站点图片并按原格式返回
    private Mono<ServerResponse> serve(ServerRequest request) {
        return siteAssets.load(request.pathVariable("filename")).flatMap(asset -> {
            var response = ServerResponse.ok()
                    .contentType(asset.mediaType())
                    .header("Cache-Control", "public, max-age=31536000, immutable")
                    .header("X-Content-Type-Options", "nosniff");
            if (SVG.equals(asset.mediaType())) {
                response.header("Content-Security-Policy", SVG_CONTENT_SECURITY_POLICY);
            }
            return response.bodyValue(asset.resource());
        });
    }

    // 返回当前接口版本
    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }
}
