package cn.tolim.pudans.core.ai.wallet.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.wallet.AiWalletService;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository.WalletTxnRecord;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiWalletEndpoint implements CustomEndpoint {

    private static final String TAG = "WalletV1alpha1";

    private static final int DEFAULT_LIMIT = 20;

    private static final int MAX_LIMIT = 100;

    private static final long MAX_CURSOR = Long.MAX_VALUE;

    private final AiWalletService walletService;

    private final AiEndpointRequests requests;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/wallet", this::getWallet, builder -> builder
                        .operationId("getAiWallet")
                        .description("Get the current user's AI wallet, creating it lazily when missing.")
                        .tag(TAG)
                        .response(responseBuilder().implementation(AiWalletService.WalletDetail.class)))
                .GET("/wallet/-/transactions", this::transactions, builder -> builder
                        .operationId("listAiWalletTransactions")
                        .description("List the current user's AI wallet transactions, newest first.")
                        .tag(TAG)
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("limit").required(false)
                                .description("Page size (1-100, default 20)."))
                        .parameter(parameterBuilder().in(ParameterIn.QUERY).name("beforeId").required(false)
                                .description("Return transactions whose id is lower than this cursor."))
                        .response(responseBuilder().implementationArray(WalletTxnRecord.class)))
                .build();
    }

    private Mono<ServerResponse> getWallet(ServerRequest request) {
        return requests.userId(request)
                .flatMap(walletService::wallet)
                .flatMap(detail -> ServerResponse.ok().bodyValue(detail));
    }

    private Mono<ServerResponse> transactions(ServerRequest request) {
        var limit = AiEndpointRequests.intParam(request, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
        var beforeId = AiEndpointRequests.longParam(request, "beforeId", MAX_CURSOR, 1, MAX_CURSOR);
        return requests.userId(request)
                .flatMap(userId -> walletService.transactions(userId, beforeId, limit).collectList())
                .flatMap(rows -> ServerResponse.ok().bodyValue(rows));
    }
}
