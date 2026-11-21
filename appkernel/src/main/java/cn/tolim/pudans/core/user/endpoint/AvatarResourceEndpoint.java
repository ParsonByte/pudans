package cn.tolim.pudans.core.user.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
import cn.tolim.pudans.core.user.AvatarStorage;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AvatarResourceEndpoint implements CustomEndpoint {

    private final AvatarStorage avatarStorage;

    // 保留旧路径的头像访问能力
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    RouterFunction<ServerResponse> legacyAvatarResourceRouter() {
        return RouterFunctions.route(GET("/avatars/{filename}"), this::serve);
    }

    // 注册头像资源访问路由
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "AvatarV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/avatars/{filename}", this::serve, builder -> builder
                        .operationId("GetAvatar")
                        .description("Get a stored avatar image.")
                        .tag(tag)
                        .response(responseBuilder()
                                .content(contentBuilder().mediaType(MediaType.IMAGE_PNG_VALUE))
                                .implementation(org.springframework.core.io.Resource.class)))
                .build();
    }

    // 读取头像文件并按图片格式返回
    private Mono<ServerResponse> serve(ServerRequest request) {
        return avatarStorage
                .load(request.pathVariable("filename"))
                .flatMap(resource -> ServerResponse.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .bodyValue(resource));
    }

    // 返回当前头像接口版本
    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }
}
