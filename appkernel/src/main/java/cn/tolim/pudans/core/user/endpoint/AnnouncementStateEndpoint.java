package cn.tolim.pudans.core.user.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.extension.api.ApiGroups;
import cn.tolim.pudans.extension.api.GroupVersion;
import cn.tolim.pudans.core.user.AnnouncementReads;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.security.core.Authentication;
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
public class AnnouncementStateEndpoint implements CustomEndpoint {

    private final AnnouncementReads announcementReads;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "UserV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/users/-/announcement-state", this::get, builder -> builder
                        .operationId("GetMyAnnouncementState")
                        .description("Get the current user's announcement dismissed state.")
                        .tag(tag)
                        .response(responseBuilder().implementation(AnnouncementReads.AnnouncementState.class)))
                .POST("/users/-/announcement-state/dismiss", this::dismiss, builder -> builder
                        .operationId("DismissMyAnnouncement")
                        .description("Mark the current announcement version as dismissed for the current user.")
                        .tag(tag)
                        .response(responseBuilder().implementation(AnnouncementReads.AnnouncementState.class)))
                .build();
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        return currentUsername()
                .flatMap(announcementReads::get)
                .flatMap(state -> ServerResponse.ok().bodyValue(state));
    }

    private Mono<ServerResponse> dismiss(ServerRequest request) {
        return currentUsername()
                .flatMap(announcementReads::dismiss)
                .flatMap(state -> ServerResponse.ok().bodyValue(state));
    }

    private Mono<String> currentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .switchIfEmpty(Mono.error(new ServerWebInputException("未认证")));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }
}
