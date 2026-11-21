package cn.tolim.pudans.core.user.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
import cn.tolim.pudans.core.user.RefreshTokens;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AuthTokenEndpoint implements CustomEndpoint {

    private final RefreshTokens refreshTokens;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "AuthTokenV1alpha1";
        return SpringdocRouteBuilder.route()
                .POST("/auth/token/refresh", this::refresh, builder -> builder
                        .operationId("RefreshAccessToken")
                        .description("Rotate a refresh token and issue a new access token.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder().required(true).implementation(RefreshRequest.class))
                        .response(responseBuilder().implementation(RefreshTokens.RefreshResult.class)))
                .build();
    }

    private Mono<ServerResponse> refresh(ServerRequest request) {
        return request.bodyToMono(RefreshRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                .flatMap(body -> {
                    if (body.refreshToken() == null || body.refreshToken().isBlank()) {
                        return Mono.error(new ServerWebInputException("refreshToken 为必填项。"));
                    }
                    return refreshTokens.rotate(body.refreshToken());
                })
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    public record RefreshRequest(String refreshToken) {}

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }
}
