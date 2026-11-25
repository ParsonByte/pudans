package cn.tolim.pudans.core.ai.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.ai.AiPriceRule;
import cn.tolim.pudans.contract.ai.dto.AiPriceRuleCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiPriceRuleUpdateRequest;
import cn.tolim.pudans.core.ai.quota.AiPriceRules;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiPriceRuleEndpoint implements CustomEndpoint {

    private static final String TAG = "AiPriceRuleV1alpha1";

    private final AiPriceRules rules;

    // 注册价格规则管理路由。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aipricerules", this::list, builder -> builder.operationId("listAiPriceRules")
                        .description("List AI model and MCP price rules.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("200").description("Price rules")))
                .POST("/aipricerules", this::create, builder -> builder.operationId("createAiPriceRule")
                        .description("Create a non-overlapping AI price rule.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AiPriceRuleCreateRequest.class))))
                        .response(responseBuilder().implementation(AiPriceRule.class)))
                .GET("/aipricerules/{name}", this::get, builder -> builder.operationId("getAiPriceRule")
                        .description("Get an AI price rule.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiPriceRule.class)))
                .PATCH("/aipricerules/{name}", this::update, builder -> builder.operationId("updateAiPriceRule")
                        .description("Update an AI price rule without overlapping its effective range.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                        .schema(schemaBuilder().implementation(AiPriceRuleUpdateRequest.class))))
                        .response(responseBuilder().implementation(AiPriceRule.class)))
                .DELETE("/aipricerules/{name}", this::delete, builder -> builder.operationId("deleteAiPriceRule")
                        .description("Delete a price rule that is not referenced by usage records.")
                        .tag(TAG)
                        .response(responseBuilder().responseCode("204").description("Price rule deleted")))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return ServerResponse.ok().body(rules.list(), AiPriceRule.class);
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(AiPriceRuleCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(rules::create)
                .flatMap(rule -> ServerResponse.status(HttpStatus.CREATED).bodyValue(rule));
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        return rules.get(id(request)).flatMap(rule -> ServerResponse.ok().bodyValue(rule));
    }

    private Mono<ServerResponse> update(ServerRequest request) {
        var id = id(request);
        return request.bodyToMono(AiPriceRuleUpdateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体不能为空。")))
                .flatMap(body -> rules.update(id, body))
                .flatMap(rule -> ServerResponse.ok().bodyValue(rule));
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        return rules.delete(id(request)).then(ServerResponse.noContent().build());
    }

    private static java.util.UUID id(ServerRequest request) {
        return AiEndpointSupport.uuid(
                request.pathVariable("name"),
                "ai-price-rule-id-invalid",
                "价格规则标识不是有效 UUID。");
    }
}
