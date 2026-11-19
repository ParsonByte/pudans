package cn.tolim.pudans.core.ai.wallet.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.export.CsvSupport;
import cn.tolim.pudans.core.ai.gateway.AiGatewayHeaders;
import cn.tolim.pudans.core.ai.wallet.AiWalletAmounts;
import cn.tolim.pudans.core.ai.wallet.AiWalletBatchAdjuster;
import cn.tolim.pudans.core.ai.wallet.AiWalletService;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletRepository.WalletAdminRecord;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository.WalletAdminTxnRecord;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
public class AiWalletAdminEndpoint implements CustomEndpoint {

    private static final String TAG = "WalletAdminV1alpha1";

    private static final long MAX_CURSOR = Long.MAX_VALUE;

    private static final int EXPORT_DEFAULT_LIMIT = 5000;

    private static final int EXPORT_MAX_LIMIT = 10000;

    private static final MediaType CSV_MEDIA_TYPE = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private static final String POINT_TXN_DISPOSITION = "attachment; filename=\"point-transactions.csv\"";

    private static final String WALLET_DISPOSITION = "attachment; filename=\"wallets.csv\"";

    private static final String POINT_TXN_HEADER =
            "id,user_id,txn_type,amount,balance_after,request_id,provider_account,model_code,tier,"
                    + "input_tokens,cache_tokens,output_tokens,billing_source,created_at\n";

    private static final String WALLET_HEADER =
            "user_id,balance,frozen,available,total_granted,total_consumed,status,daily_limit\n";

    private final AiWalletService walletService;

    private final AiWalletBatchAdjuster batchAdjuster;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET("/wallets", this::list, builder -> builder
                        .operationId("listAiWallets")
                        .description("List all AI wallets.")
                        .tag(TAG)
                        .parameter(queryParameter("beforeId"))
                        .parameter(queryParameter("limit"))
                        .response(responseBuilder().implementationArray(WalletAdminRecord.class)))
                .GET("/wallets/export", this::exportWallets, builder -> builder
                        .operationId("exportAiWallets")
                        .description("Export AI wallets as CSV.")
                        .tag(TAG)
                        .parameter(exportLimitParameter())
                        .response(responseBuilder().description("Wallet CSV export.")))
                .POST("/wallets/-/batch-adjust", this::batchAdjust, builder -> builder
                        .operationId("batchAdjustAiWallets")
                        .description("Adjust multiple user wallets from a CSV body.")
                        .tag(TAG)
                        .requestBody(requestBodyBuilder()
                                .required(true)
                                .content(contentBuilder()
                                        .mediaType("text/csv")
                                        .schema(schemaBuilder().implementation(String.class))))
                        .response(responseBuilder().implementation(AiWalletBatchAdjuster.BatchAdjustResult.class)))
                .POST("/wallets/{userId}/adjust", this::adjust, builder -> builder
                        .operationId("adjustAiWallet")
                        .description("Grant or deduct points for a user wallet.")
                        .tag(TAG)
                        .parameter(userParameter())
                        .requestBody(requestBodyBuilder().required(true).implementation(AdjustRequest.class))
                        .response(responseBuilder().implementation(AiWalletService.WalletDetail.class)))
                .POST("/wallets/{userId}/freeze", this::freeze, builder -> builder
                        .operationId("freezeAiWallet")
                        .description("Freeze a user wallet.")
                        .tag(TAG)
                        .parameter(userParameter())
                        .response(responseBuilder().implementation(AiWalletService.WalletDetail.class)))
                .POST("/wallets/{userId}/unfreeze", this::unfreeze, builder -> builder
                        .operationId("unfreezeAiWallet")
                        .description("Unfreeze a user wallet.")
                        .tag(TAG)
                        .parameter(userParameter())
                        .response(responseBuilder().implementation(AiWalletService.WalletDetail.class)))
                .GET("/pointtransactions", this::transactions, builder -> builder
                        .operationId("listAllPointTransactions")
                        .description("List all point transactions.")
                        .tag(TAG)
                        .parameter(queryParameter("beforeId"))
                        .parameter(queryParameter("limit"))
                        .response(responseBuilder().implementationArray(WalletAdminTxnRecord.class)))
                .GET("/pointtransactions/-/export", this::exportTransactions, builder -> builder
                        .operationId("exportPointTransactions")
                        .description("Export point transactions as CSV.")
                        .tag(TAG)
                        .parameter(queryParameter("userId"))
                        .parameter(queryParameter("from"))
                        .parameter(queryParameter("to"))
                        .parameter(exportLimitParameter())
                        .response(responseBuilder().description("Point transaction CSV export.")))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return walletService
                .wallets(
                        AiEndpointRequests.longParam(request, "beforeId", MAX_CURSOR, 1, MAX_CURSOR),
                        AiEndpointRequests.intParam(request, "limit", 20, 1, 100))
                .collectList()
                .flatMap(wallets -> ServerResponse.ok().bodyValue(wallets));
    }

    private Mono<ServerResponse> exportWallets(ServerRequest request) {
        var limit = AiEndpointRequests.intParam(request, "limit", EXPORT_DEFAULT_LIMIT, 1, EXPORT_MAX_LIMIT);
        var body = Flux.concat(
                Mono.just(WALLET_HEADER),
                walletService.wallets(MAX_CURSOR, limit).map(AiWalletAdminEndpoint::walletLine));
        return csvResponse(body, WALLET_DISPOSITION);
    }

    private Mono<ServerResponse> batchAdjust(ServerRequest request) {
        var header = request.headers().firstHeader(AiGatewayHeaders.IDEMPOTENCY);
        var batchId = StringUtils.hasText(header) ? header.trim() : UUID.randomUUID().toString();
        return request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(csv -> batchAdjuster.adjust(batchId, csv))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> adjust(ServerRequest request) {
        return request.bodyToMono(AdjustRequest.class)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST, "ai-wallet-adjust-invalid", "请求体不能为空。")))
                .flatMap(body -> walletService
                        .adjust(
                                StringUtils.hasText(body.requestId())
                                        ? body.requestId()
                                        : UUID.randomUUID().toString(),
                                request.pathVariable("userId"),
                                body.amount())
                        .flatMap(wallet -> ServerResponse.ok().bodyValue(wallet)));
    }

    private Mono<ServerResponse> freeze(ServerRequest request) {
        return walletService
                .freezeAccount(request.pathVariable("userId"))
                .flatMap(wallet -> ServerResponse.ok().bodyValue(wallet));
    }

    private Mono<ServerResponse> unfreeze(ServerRequest request) {
        return walletService
                .unfreezeAccount(request.pathVariable("userId"))
                .flatMap(wallet -> ServerResponse.ok().bodyValue(wallet));
    }

    private Mono<ServerResponse> transactions(ServerRequest request) {
        return walletService
                .allTransactions(
                        AiEndpointRequests.longParam(request, "beforeId", MAX_CURSOR, 1, MAX_CURSOR),
                        AiEndpointRequests.intParam(request, "limit", 20, 1, 100))
                .collectList()
                .flatMap(transactions -> ServerResponse.ok().bodyValue(transactions));
    }

    private Mono<ServerResponse> exportTransactions(ServerRequest request) {
        var userId = request.queryParam("userId").map(String::trim).filter(StringUtils::hasText).orElse(null);
        var from = instantParam(request, "from");
        var to = instantParam(request, "to");
        var limit = AiEndpointRequests.intParam(request, "limit", EXPORT_DEFAULT_LIMIT, 1, EXPORT_MAX_LIMIT);
        var body = Flux.concat(
                Mono.just(POINT_TXN_HEADER),
                walletService.exportTransactions(userId, from, to, limit)
                        .map(AiWalletAdminEndpoint::transactionLine));
        return csvResponse(body, POINT_TXN_DISPOSITION);
    }

    private static Mono<ServerResponse> csvResponse(Flux<String> body, String disposition) {
        return ServerResponse.ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(body, String.class);
    }

    private static String walletLine(WalletAdminRecord wallet) {
        return CsvSupport.line(
                wallet.userId(),
                wallet.balance().toPlainString(),
                wallet.frozen().toPlainString(),
                AiWalletAmounts.available(wallet.balance(), wallet.frozen()).toPlainString(),
                wallet.totalGranted().toPlainString(),
                wallet.totalConsumed().toPlainString(),
                Integer.toString(wallet.status()),
                decimal(wallet.dailyLimit()));
    }

    private static String transactionLine(WalletAdminTxnRecord transaction) {
        return CsvSupport.line(
                Long.toString(transaction.id()),
                transaction.userId(),
                transaction.txnType(),
                transaction.amount().toPlainString(),
                transaction.balanceAfter().toPlainString(),
                transaction.requestId(),
                transaction.providerAccount(),
                transaction.modelCode(),
                transaction.tier(),
                integer(transaction.inputTokens()),
                integer(transaction.cacheTokens()),
                integer(transaction.outputTokens()),
                transaction.billingSource(),
                DateTimeFormatter.ISO_INSTANT.format(transaction.createdAt()));
    }

    private static String decimal(@Nullable BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String integer(@Nullable Integer value) {
        return value == null ? "" : value.toString();
    }

    private static @Nullable Instant instantParam(ServerRequest request, String name) {
        var value = request.queryParam(name).map(String::trim).filter(StringUtils::hasText).orElse(null);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException error) {
            throw new ServerWebInputException("查询参数 '" + name + "' 必须是 ISO-8601 时间。");
        }
    }

    private static org.springdoc.core.fn.builders.parameter.Builder userParameter() {
        return parameterBuilder().in(ParameterIn.PATH).name("userId").required(true);
    }

    private static org.springdoc.core.fn.builders.parameter.Builder queryParameter(String name) {
        return parameterBuilder().in(ParameterIn.QUERY).name(name).required(false);
    }

    private static org.springdoc.core.fn.builders.parameter.Builder exportLimitParameter() {
        return parameterBuilder()
                .in(ParameterIn.QUERY)
                .name("limit")
                .required(false)
                .description("Maximum rows (1-10000, default 5000).");
    }

    public record AdjustRequest(BigDecimal amount, String requestId) {}
}
