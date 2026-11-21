package cn.tolim.pudans.core.ai.library.skill.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.contract.ai.AiSkill;
import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.harness.skill.curator.SkillAuditLog;
import cn.tolim.pudans.core.ai.harness.skill.curator.SkillPromotion;
import cn.tolim.pudans.core.ai.harness.skill.curator.SkillUsageRecord;
import cn.tolim.pudans.core.ai.library.skill.Skills;
import cn.tolim.pudans.core.ai.team.Teams;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiSkillEndpoint implements CustomEndpoint {

    private static final String TAG = "AiSkillV1alpha1";

    private final Skills skillService;

    private final Teams teams;

    private final AiEndpointRequests requests;

    private final SkillPromotion skillPromotion;

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
                .GET("/aiskills/-/usage", this::usage, builder -> builder
                        .operationId("listAiSkillUsage")
                        .description("List persisted skill usage records for the curator.")
                        .tag(TAG)
                        .response(responseBuilder().implementationArray(SkillUsageRecord.class)))
                .GET("/aiskills/-/audit", this::audit, builder -> builder
                        .operationId("listAiSkillAudit")
                        .description("List recent skill curator audit entries.")
                        .tag(TAG)
                        .parameter(limitParameter())
                        .response(responseBuilder().implementationArray(SkillAuditLog.Entry.class)))
                .POST("/aiskills/{name}/actions/promote", this::promote, builder -> builder
                        .operationId("promoteAiSkill")
                        .description("Publish a draft skill after the curator review.")
                        .tag(TAG)
                        .parameter(nameParameter())
                        .response(responseBuilder().implementation(SkillPromotion.Result.class)))
                .POST("/aiskills/{name}/actions/archive", this::archive, builder -> builder
                        .operationId("archiveAiSkill")
                        .description("Archive a published skill and record the curator audit.")
                        .tag(TAG)
                        .parameter(nameParameter())
                        .response(responseBuilder().implementation(SkillPromotion.Result.class)))
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
                .flatMap(userId -> teams.requireAgent(agentName, userId))
                .flatMapMany(agent -> skillService.byAgent(agent.getMetadata().getName()))
                .collectList()
                .flatMap(skills -> ServerResponse.ok().bodyValue(skills));
    }

    private Mono<ServerResponse> usage(ServerRequest request) {
        return requests
                .userId(request)
                .flatMapMany(userId -> skillPromotion.usage().flatMapMany(Flux::fromIterable))
                .collectList()
                .flatMap(records -> ServerResponse.ok().bodyValue(records));
    }

    private Mono<ServerResponse> audit(ServerRequest request) {
        var limit = request.queryParam("limit")
                .filter(StringUtils::hasText)
                .map(value -> {
                    try {
                        return Math.max(1, Integer.parseInt(value.strip()));
                    } catch (NumberFormatException error) {
                        throw new ServerWebInputException("查询参数 'limit' 必须是整数。");
                    }
                })
                .orElse(100);
        return requests
                .userId(request)
                .flatMapMany(userId -> skillPromotion.audit(limit).flatMapMany(Flux::fromIterable))
                .collectList()
                .flatMap(entries -> ServerResponse.ok().bodyValue(entries));
    }

    private Mono<ServerResponse> promote(ServerRequest request) {
        var name = request.pathVariable("name");
        return requests
                .userId(request)
                .flatMap(actor -> skillPromotion.promote(name, actor))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> archive(ServerRequest request) {
        var name = request.pathVariable("name");
        return requests
                .userId(request)
                .flatMap(actor -> skillPromotion.archive(name, actor))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private static String agent(ServerRequest request) {
        return request.queryParam("agent")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ServerWebInputException("查询参数 'agent' 不能为空。"));
    }

    private static Builder agentParameter() {
        return parameterBuilder().in(ParameterIn.QUERY).name("agent").required(true);
    }

    private static Builder nameParameter() {
        return parameterBuilder().in(ParameterIn.PATH).name("name").required(true);
    }

    private static Builder limitParameter() {
        return parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false);
    }

    public record SkillImportRequest(@Nullable String url, @Nullable String ref) {}
}