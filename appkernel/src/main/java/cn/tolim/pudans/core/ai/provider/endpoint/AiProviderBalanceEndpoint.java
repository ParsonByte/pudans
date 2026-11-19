package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekBalanceClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekProviderAccess;
import cn.tolim.pudans.infra.exception.ApiException;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.fn.builders.parameter.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiProviderBalanceEndpoint implements CustomEndpoint {

    private static final String TAG = "AiProviderAccountV1alpha1";

    private static final String CODE_ACCOUNT_INVALID = "ai-provider-balance-account-invalid";

    private final ReactiveExtensionClient client;

    private final DeepSeekProviderAccess access;

    private final DeepSeekBalanceClient balanceClient;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/aiprovideraccounts/{name}/balance", this::balance, builder -> builder
                        .operationId("getAiProviderAccountBalance")
                        .description("Query the provider account balance.")
                        .tag(TAG)
                        .parameter(pathParameter("name", "metadata.name of the provider account."))
                        .response(responseBuilder().implementation(DeepSeekBalanceClient.DeepSeekBalance.class)))
                .build();
    }

    private Mono<ServerResponse> balance(ServerRequest request) {
        return client.get(AiProviderAccount.class, request.pathVariable("name"))
                .flatMap(account -> {
                    var spec = account.getSpec();
                    if (spec == null || !spec.isEnabled()) {
                        return Mono.error(new ApiException(
                                HttpStatus.BAD_REQUEST, CODE_ACCOUNT_INVALID, "账号不存在或已禁用。"));
                    }
                    return access.requireProviderOf(account)
                            .flatMap(provider -> balanceClient.get(provider, account));
                })
                .flatMap(balance -> ServerResponse.ok().bodyValue(balance));
    }

    private static Builder pathParameter(String name, String description) {
        return parameterBuilder().in(ParameterIn.PATH).name(name).required(true).description(description);
    }
}
