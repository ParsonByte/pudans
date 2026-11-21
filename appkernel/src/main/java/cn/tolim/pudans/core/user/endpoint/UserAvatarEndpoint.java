package cn.tolim.pudans.core.user.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.core.user.AvatarStorage;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UserAvatarEndpoint implements CustomEndpoint {

    private final Accounts accounts;

    private final ReactiveExtensionClient client;

    private final AvatarStorage avatarStorage;

    // 注册上传与删除头像的路由
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "UserV1alpha1";
        return SpringdocRouteBuilder.route()
                .POST("/users/-/avatar", this::upload, builder -> builder.operationId("UploadMyAvatar")
                        .description("Upload and resize the current user's avatar.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder().mediaType(MediaType.MULTIPART_FORM_DATA_VALUE)))
                        .response(responseBuilder().implementation(UserView.class)))
                .DELETE("/users/-/avatar", this::delete, builder -> builder.operationId("DeleteMyAvatar")
                        .description("Delete the current user's avatar.")
                        .tag(tag)
                        .response(responseBuilder().implementation(UserView.class)))
                .build();
    }

    // 解析表单文件，保存并更新头像
    private Mono<ServerResponse> upload(ServerRequest request) {
        return currentUser()
                .flatMap(user -> request.multipartData()
                        .flatMap(parts -> {
                            var part = parts.getFirst("file");
                            if (!(part instanceof org.springframework.http.codec.multipart.FilePart filePart)) {
                                return Mono.error(
                                        new ServerWebInputException("需要名为 file 的 multipart 文件字段。"));
                            }
                            return avatarStorage.store(user, filePart);
                        })
                        .flatMap(url -> updateAvatar(user, url)))
                .flatMap(user -> ServerResponse.ok().bodyValue(UserView.from(user, true)));
    }

    // 删除头像文件并清空头像地址
    private Mono<ServerResponse> delete(ServerRequest request) {
        return currentUser()
                .flatMap(user -> avatarStorage.deleteForUser(user).thenReturn(user))
                .flatMap(user -> updateAvatar(user, null))
                .flatMap(user -> ServerResponse.ok().bodyValue(UserView.from(user, true)));
    }

    // 将头像地址写回用户记录
    private Mono<User> updateAvatar(User user, String avatar) {
        user.getSpec().setAvatar(avatar);
        return client.update(user);
    }

    // 从安全上下文解析当前用户
    private Mono<User> currentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .map(org.springframework.security.core.Authentication::getName)
                .switchIfEmpty(Mono.error(new ServerWebInputException("未认证")))
                .flatMap(accounts::getUser);
    }

    // 返回当前头像接口版本
    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }
}
