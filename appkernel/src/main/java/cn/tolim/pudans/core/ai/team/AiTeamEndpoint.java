package cn.tolim.pudans.core.ai.team;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.core.extension.ai.AiTeam;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiTeamEndpoint implements CustomEndpoint {

    private final AiTeamService teamService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "AiTeamV1alpha1";
        return SpringdocRouteBuilder.route()
                .POST("/aiteams/{name}/members", this::addMember, builder -> builder
                        .operationId("addAiTeamMember")
                        .description("Add or reactivate a member of an AI team.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(org.springdoc.core.fn.builders.content.Builder.contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(org.springdoc.core.fn.builders.schema.Builder.schemaBuilder()
                                                .implementation(MemberRequest.class))))
                        .response(responseBuilder().implementation(AiTeam.class)))
                .DELETE("/aiteams/{name}/members/{userId}", this::removeMember, builder -> builder
                        .operationId("removeAiTeamMember")
                        .description("Mark a member of an AI team as removed.")
                        .tag(tag)
                        .response(responseBuilder().implementation(AiTeam.class)))
                .build();
    }

    private Mono<ServerResponse> addMember(ServerRequest request) {
        var teamName = request.pathVariable("name");
        return request.bodyToMono(MemberRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> {
                    if (body.userId() == null || body.userId().isBlank()) {
                        return Mono.error(new ServerWebInputException("userId 不能为空。"));
                    }
                    return teamService.addMember(teamName, body.userId(), body.role());
                })
                .flatMap(team -> ServerResponse.ok().bodyValue(team));
    }

    private Mono<ServerResponse> removeMember(ServerRequest request) {
        return teamService
                .removeMember(request.pathVariable("name"), request.pathVariable("userId"))
                .flatMap(team -> ServerResponse.ok().bodyValue(team));
    }

    public record MemberRequest(String userId, AiTeam.TeamRole role) {}
}
