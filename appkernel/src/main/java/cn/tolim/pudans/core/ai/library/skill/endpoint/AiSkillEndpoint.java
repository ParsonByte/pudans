package cn.tolim.pudans.core.ai.library.skill.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.core.extension.ai.AiSkill;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.library.skill.SkillService;
import cn.tolim.pudans.core.ai.team.AiTeamService;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.fn.builders.parameter.Builder;
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
public class AiSkillEndpoint implements CustomEndpoint {

    private static final String TAG = "AiSkillV1alpha1";

    private final SkillService skillService;

    private final AiTeamService teamService;

    private final AiEndpointRequests requests;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .POST("/aiskills/-/import", this::importSkill, builder -> builder
                        .operationId("importAiSkill")
                        .description("Fetch a SKILL.md document and return an unsaved skill draft.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder().required(true).implementation(SkillImportRequest.class))
                        .response(responseBuilder().implementation(AiSkill.class)))
                .GET("/aiskills/-/by-agent", this::byAgent, builder -> builder
                        .operationId("listAiSkillsByAgent")
                        .description("List enabled skills bound to an agent.")
                        .tag(TAG)
                        .parameter(agentParameter())
                        .response(responseBuilder().implementationArray(AiSkill.class)))
                .build();
    }

    private Mono<ServerResponse> importSkill(ServerRequest request) {
        return requests
                .userId(request)
                .then(request.bodyToMono(SkillImportRequest.class)
                        .switchIfEmpty(Mono.error(
                                new ServerWebInputException("请求体不能为空。"))))
                .flatMap(body -> skillService.importFromUrl(body.url(), body.ref()))
                .flatMap(skill -> ServerResponse.ok().bodyValue(skill));
    }

    private Mono<ServerResponse> byAgent(ServerRequest request) {
        var agentName = agent(request);
        return requests
                .userId(request)
                .flatMap(userId -> teamService.requireAgent(agentName, userId))
                .flatMapMany(agent -> skillService.byAgent(agent.getMetadata().getName()))
                .collectList()
                .flatMap(skills -> ServerResponse.ok().bodyValue(skills));
    }

    private static String agent(ServerRequest request) {
        return request.queryParam("agent")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ServerWebInputException("查询参数 'agent' 不能为空。"));
    }

    private static Builder agentParameter() {
        return parameterBuilder().in(ParameterIn.QUERY).name("agent").required(true);
    }

    public record SkillImportRequest(@Nullable String url, @Nullable String ref) {}
}