package cn.tolim.pudans.core.ai.wallet;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletHoldRepository;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletHoldRepository.Claim;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletRepository;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletRepository.WalletAdminRecord;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository.TxnSummary;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository.WalletAdminTxnRecord;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository.WalletTxn;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository.WalletTxnRecord;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiWalletService {

    private static final String SETTLE_KEY_PREFIX = "settle:";

    private static final String RELEASE_KEY_PREFIX = "release:";

    private static final String TXN_TYPE_CHARGE = "CHARGE";

    private static final String TXN_TYPE_UNFREEZE = "UNFREEZE";

    private static final String CODE_INSUFFICIENT = "ai-wallet-insufficient";

    private static final String CODE_AMOUNT_INVALID = "ai-wallet-amount-invalid";

    private static final String CODE_EXPIRY_INVALID = "ai-wallet-expiry-invalid";

    private static final String CODE_HOLD_CONFLICT = "ai-wallet-hold-conflict";

    private static final String CODE_WALLET_MISSING = "ai-wallet-wallet-missing";

    private static final String CODE_SETTLE_CONFLICT = "ai-wallet-settle-conflict";

    private static final String CODE_RELEASE_CONFLICT = "ai-wallet-release-conflict";

    private static final String CODE_ADJUST_CONFLICT = "ai-wallet-adjust-conflict";

    private static final String ADJUST_KEY_PREFIX = "admin-adjust:";

    static final int SWEEP_BATCH_SIZE = 100;

    private final AiWalletRepository walletRepository;

    private final AiWalletHoldRepository holdRepository;

    private final AiWalletTxnRepository txnRepository;

    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public Mono<Void> ensureWallet(String userId) {
        Assert.hasText(userId, "userId 不能为空");
        return walletRepository.ensure(userId);
    }

    public Mono<WalletDetail> wallet(String userId) {
        Assert.hasText(userId, "userId 不能为空");
        // 钱包按需创建，首次读取返回零余额而不是 404。
        return ensureWallet(userId)
                .then(walletRepository.findDetailByUserId(userId))
                .switchIfEmpty(Mono.error(walletMissing()))
                .map(record -> new WalletDetail(
                        record.balance(),
                        record.frozen(),
                        AiWalletAmounts.available(record.balance(), record.frozen()),
                        record.totalGranted(),
                        record.totalConsumed(),
                        record.status(),
                        record.dailyLimit()));
    }

    public Flux<WalletTxnRecord> transactions(String userId, long beforeId, int limit) {
        Assert.hasText(userId, "userId 不能为空");
        Assert.isTrue(beforeId > 0, "流水游标必须大于零");
        Assert.isTrue(limit > 0, "流水数量必须大于零");
        return txnRepository.listByUser(userId, beforeId, limit);
    }

    public Flux<WalletAdminRecord> wallets(long beforeId, int limit) {
        Assert.isTrue(beforeId > 0, "钱包游标必须大于零");
        Assert.isTrue(limit > 0, "钱包数量必须大于零");
        return walletRepository.list(beforeId, limit);
    }

    public Flux<WalletAdminTxnRecord> allTransactions(long beforeId, int limit) {
        Assert.isTrue(beforeId > 0, "流水游标必须大于零");
        Assert.isTrue(limit > 0, "流水数量必须大于零");
        return txnRepository.listAll(beforeId, limit);
    }

    public Flux<WalletAdminTxnRecord> exportTransactions(
            @Nullable String userId, @Nullable Instant from, @Nullable Instant to, int limit) {
        Assert.isTrue(limit > 0, "流水数量必须大于零");
        return txnRepository.export(userId, from, to, limit);
    }

    public Mono<WalletDetail> adjust(String requestId, String userId, BigDecimal amount) {
        Assert.hasText(requestId, "requestId 不能为空");
        Assert.hasText(userId, "userId 不能为空");
        if (amount == null || amount.signum() == 0) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_AMOUNT_INVALID, "调整金额不能为零。"));
        }
        var idempotencyKey = ADJUST_KEY_PREFIX + requestId;
        return txnRepository
                .findByIdempotencyKey(idempotencyKey)
                .flatMap(existing -> wallet(userId))
                .switchIfEmpty(Mono.defer(() -> ensureWallet(userId)
                        .then(walletRepository.adjust(userId, amount))
                        .switchIfEmpty(Mono.error(new ApiException(
                                HttpStatus.CONFLICT,
                                CODE_ADJUST_CONFLICT,
                                "调整失败：余额不足或钱包已冻结。")))
                        .flatMap(snapshot -> txnRepository
                                .insertIfAbsent(new WalletTxn(
                                        userId,
                                        amount.signum() > 0 ? "GRANT" : "DEDUCT",
                                        amount,
                                        snapshot.balance(),
                                        requestId,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        idempotencyKey))
                                .then(wallet(userId)))));
    }

    public Mono<WalletDetail> freezeAccount(String userId) {
        return setAccountStatus(userId, 0);
    }

    public Mono<WalletDetail> unfreezeAccount(String userId) {
        return setAccountStatus(userId, 1);
    }

    private Mono<WalletDetail> setAccountStatus(String userId, int status) {
        Assert.hasText(userId, "userId 不能为空");
        return ensureWallet(userId)
                .then(walletRepository.setStatus(userId, status))
                .then(wallet(userId));
    }

    public Mono<FreezeResult> freeze(String requestId, String userId, BigDecimal amount, Duration expiry) {
        Assert.hasText(requestId, "requestId 不能为空");
        Assert.hasText(userId, "userId 不能为空");
        var freezeAmount = AiWalletAmounts.requireNonNegative(amount, "冻结金额");
        if (expiry == null || expiry.isZero() || expiry.isNegative()) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_EXPIRY_INVALID, "冻结有效期必须大于零。"));
        }
        var expiresAt = clock.instant().plus(expiry);
        return holdRepository
                .insertIfAbsent(requestId, userId, freezeAmount, expiresAt)
                .flatMap(inserted -> Boolean.TRUE.equals(inserted)
                        ? freezeNewHold(requestId, userId, freezeAmount)
                        : replayFreeze(requestId, userId));
    }

    public Mono<SettleResult> settle(
            String requestId,
            String userId,
            BigDecimal holdAmount,
            BigDecimal actualCost,
            @Nullable AiWalletUsage usage) {
        Assert.hasText(requestId, "requestId 不能为空");
        Assert.hasText(userId, "userId 不能为空");
        var frozenAmount = AiWalletAmounts.requireNonNegative(holdAmount, "冻结金额");
        var cost = AiWalletAmounts.requireNonNegative(actualCost, "实际费用");
        var usageInfo = usage == null ? AiWalletUsage.EMPTY : usage;
        var idempotencyKey = SETTLE_KEY_PREFIX + requestId;
        return txnRepository
                .findByIdempotencyKey(idempotencyKey)
                .map(existing -> duplicateSettle(requestId, existing))
                .switchIfEmpty(Mono.defer(
                        () -> settleNewCharge(requestId, userId, frozenAmount, cost, usageInfo, idempotencyKey)));
    }

    public Mono<ReleaseResult> release(String requestId) {
        Assert.hasText(requestId, "requestId 不能为空");
        var idempotencyKey = RELEASE_KEY_PREFIX + requestId;
        return holdRepository
                .claimReleased(requestId)
                .flatMap(claim -> walletRepository
                        .releaseFrozen(claim.userId(), claim.amount())
                        .switchIfEmpty(Mono.error(new ApiException(
                                HttpStatus.CONFLICT, CODE_RELEASE_CONFLICT, "释放失败：冻结额度不足。")))
                        .flatMap(snapshot -> txnRepository
                                .insertIfAbsent(unfreezeTxn(requestId, claim, snapshot.balance(), idempotencyKey))
                                .map(inserted -> new ReleaseResult(requestId, true, idempotencyKey))))
                .switchIfEmpty(Mono.just(new ReleaseResult(requestId, false, idempotencyKey)));
    }

    public Mono<SweepResult> sweepExpired() {
        return holdRepository
                .findExpiredRequestIds(SWEEP_BATCH_SIZE)
                .concatMap(requestId -> release(requestId).map(ReleaseResult::released))
                .filter(Boolean::booleanValue)
                .count()
                .map(SweepResult::new);
    }

    private Mono<FreezeResult> freezeNewHold(String requestId, String userId, BigDecimal amount) {
        return walletRepository
                .reserve(userId, amount)
                .map(snapshot ->
                        new FreezeResult(requestId, userId, amount, snapshot.balance(), snapshot.frozen(), false))
                .switchIfEmpty(Mono.defer(() -> holdRepository
                        .deleteIfHeld(requestId)
                        .then(Mono.error(new ApiException(
                                HttpStatus.PAYMENT_REQUIRED,
                                CODE_INSUFFICIENT,
                                "积分余额不足，无法冻结。")))));
    }

    private Mono<FreezeResult> replayFreeze(String requestId, String userId) {
        return holdRepository
                .findClaim(requestId)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.CONFLICT, CODE_HOLD_CONFLICT, "冻结请求已存在但无法读取。")))
                .flatMap(claim -> {
                    if (!claim.userId().equals(userId)) {
                        return Mono.error(new ApiException(
                                HttpStatus.CONFLICT, CODE_HOLD_CONFLICT, "冻结请求已绑定其他用户。"));
                    }
                    return walletRepository
                            .findByUserId(userId)
                            .switchIfEmpty(Mono.error(new ApiException(
                                    HttpStatus.CONFLICT, CODE_WALLET_MISSING, "用户钱包不存在。")))
                            .map(snapshot -> new FreezeResult(
                                    requestId, userId, claim.amount(), snapshot.balance(), snapshot.frozen(), true));
                });
    }

    private Mono<SettleResult> settleNewCharge(
            String requestId,
            String userId,
            BigDecimal holdAmount,
            BigDecimal actualCost,
            AiWalletUsage usage,
            String idempotencyKey) {
        return holdRepository
                .claimSettled(requestId, userId)
                .flatMap(claim -> {
                    var chargedCost = AiWalletAmounts.chargedCost(actualCost, holdAmount);
                    return walletRepository
                            .deductForSettle(userId, claim.amount(), chargedCost)
                            .switchIfEmpty(Mono.error(new ApiException(
                                    HttpStatus.CONFLICT,
                                    CODE_SETTLE_CONFLICT,
                                    "结算失败：冻结额度不足或钱包状态异常。")))
                            .flatMap(snapshot -> txnRepository
                                    .insertIfAbsent(chargeTxn(
                                            requestId, userId, chargedCost, snapshot.balance(), usage, idempotencyKey))
                                    .flatMap(inserted -> Boolean.TRUE.equals(inserted)
                                            ? Mono.just(new SettleResult(
                                                    requestId,
                                                    userId,
                                                    chargedCost,
                                                    snapshot.balance(),
                                                    idempotencyKey,
                                                    false))
                                            : replaySettle(requestId, idempotencyKey)));
                })
                .switchIfEmpty(Mono.defer(() -> replaySettle(requestId, idempotencyKey)));
    }

    private Mono<SettleResult> replaySettle(String requestId, String idempotencyKey) {
        return txnRepository
                .findByIdempotencyKey(idempotencyKey)
                .map(existing -> duplicateSettle(requestId, existing))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        CODE_SETTLE_CONFLICT,
                        "结算失败：冻结记录不存在或状态已变更。")));
    }

    private static SettleResult duplicateSettle(String requestId, TxnSummary existing) {
        return new SettleResult(
                requestId,
                existing.userId(),
                existing.amount().abs(),
                existing.balanceAfter(),
                existing.idempotencyKey(),
                true);
    }

    private static WalletTxn chargeTxn(
            String requestId,
            String userId,
            BigDecimal chargedCost,
            BigDecimal balanceAfter,
            AiWalletUsage usage,
            String idempotencyKey) {
        return new WalletTxn(
                userId,
                TXN_TYPE_CHARGE,
                chargedCost.negate(),
                balanceAfter,
                requestId,
                usage.providerAccount(),
                usage.modelCode(),
                usage.tier(),
                usage.inputTokens(),
                usage.cacheTokens(),
                usage.outputTokens(),
                usage.traceId(),
                usage.billingSource(),
                idempotencyKey,
                usage.remark());
    }

    private static WalletTxn unfreezeTxn(
            String requestId, Claim claim, BigDecimal balanceAfter, String idempotencyKey) {
        return new WalletTxn(
                claim.userId(),
                TXN_TYPE_UNFREEZE,
                BigDecimal.ZERO,
                balanceAfter,
                requestId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                idempotencyKey);
    }

    public record FreezeResult(
            String requestId,
            String userId,
            BigDecimal amount,
            BigDecimal balance,
            BigDecimal frozen,
            boolean duplicate) {

        public BigDecimal available() {
            return AiWalletAmounts.available(balance, frozen);
        }
    }

    public record SettleResult(
            String requestId,
            String userId,
            BigDecimal chargedCost,
            BigDecimal balance,
            String idempotencyKey,
            boolean duplicate) {
    }

    public record ReleaseResult(String requestId, boolean released, String idempotencyKey) {
    }

    public record SweepResult(long releasedCount) {
    }

    public record WalletDetail(
            BigDecimal balance,
            BigDecimal frozen,
            BigDecimal available,
            BigDecimal totalGranted,
            BigDecimal totalConsumed,
            int status,
            @Nullable BigDecimal dailyLimit) {
    }

    private static ApiException walletMissing() {
        return new ApiException(HttpStatus.NOT_FOUND, CODE_WALLET_MISSING, "用户钱包不存在。");
    }
}
