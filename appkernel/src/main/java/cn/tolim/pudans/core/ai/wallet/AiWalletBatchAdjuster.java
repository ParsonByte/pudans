package cn.tolim.pudans.core.ai.wallet;

import cn.tolim.pudans.core.ai.export.CsvSupport;
import cn.tolim.pudans.infra.exception.ApiException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AiWalletBatchAdjuster {

    public static final int MAX_ROWS = 1000;

    private static final HexFormat HEX = HexFormat.of();

    private static final String CODE_BATCH_INVALID = "ai-wallet-batch-invalid";

    private static final String CODE_AMOUNT_INVALID = "ai-wallet-amount-invalid";

    private static final String STATUS_SUCCESS = "SUCCESS";

    private static final String STATUS_FAILED = "FAILED";

    private static final String IDEMPOTENCY_PREFIX = "batch:";

    private static final String HEADER_USER_ID = "userId";

    private static final String HEADER_AMOUNT = "amount";

    private static final String HEADER_REQUEST_ID = "requestId";

    private static final String BOM = "\uFEFF";

    private final AiWalletService walletService;

    public Mono<BatchAdjustResult> adjust(String batchId, String csv) {
        return Mono.defer(() -> {
            var rows = parse(csv);
            return Flux.fromIterable(rows)
                    .concatMap(row -> applyRow(batchId, row))
                    .collectList()
                    .map(results -> summarize(batchId, results));
        });
    }

    private static List<ParsedRow> parse(String csv) {
        if (!StringUtils.hasText(csv)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CODE_BATCH_INVALID, "CSV 内容不能为空。");
        }
        if (csv.startsWith(BOM)) {
            csv = csv.substring(1);
        }
        var rows = new ArrayList<ParsedRow>();
        var firstContentLine = true;
        for (var line : csv.split("\\R", -1)) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            List<String> fields;
            try {
                fields = CsvSupport.parseLine(line);
            } catch (IllegalArgumentException error) {
                throw new ApiException(HttpStatus.BAD_REQUEST, CODE_BATCH_INVALID, error.getMessage());
            }
            if (firstContentLine && isHeader(fields)) {
                firstContentLine = false;
                continue;
            }
            firstContentLine = false;
            if (rows.size() >= MAX_ROWS) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, CODE_BATCH_INVALID, "单次最多调整 " + MAX_ROWS + " 行。");
            }
            rows.add(parseRow(rows.size() + 1, fields));
        }
        return rows;
    }

    private static boolean isHeader(List<String> fields) {
        return fields.size() == 3
                && HEADER_USER_ID.equalsIgnoreCase(fields.get(0).trim())
                && HEADER_AMOUNT.equalsIgnoreCase(fields.get(1).trim())
                && HEADER_REQUEST_ID.equalsIgnoreCase(fields.get(2).trim());
    }

    private static ParsedRow parseRow(int rowNumber, List<String> fields) {
        if (fields.size() != 3) {
            return new ParsedRow(
                    rowNumber,
                    fields.isEmpty() ? null : fields.get(0).trim(),
                    null,
                    null,
                    CODE_BATCH_INVALID,
                    "每行必须包含 userId,amount,requestId 三列。");
        }
        var userId = fields.get(0).trim();
        var amountText = fields.get(1).trim();
        var requestId = fields.get(2).trim();
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText);
        } catch (NumberFormatException error) {
            amount = null;
        }
        String errorCode = null;
        String errorMessage = null;
        if (!StringUtils.hasText(userId)) {
            errorCode = CODE_BATCH_INVALID;
            errorMessage = "userId 不能为空。";
        } else if (amount == null) {
            errorCode = CODE_AMOUNT_INVALID;
            errorMessage = "金额必须是有效数字。";
        } else if (amount.signum() == 0) {
            errorCode = CODE_AMOUNT_INVALID;
            errorMessage = "调整金额不能为零。";
        }
        return new ParsedRow(
                rowNumber,
                StringUtils.hasText(userId) ? userId : null,
                amount,
                StringUtils.hasText(requestId) ? requestId : null,
                errorCode,
                errorMessage);
    }

    private Mono<RowResult> applyRow(String batchId, ParsedRow row) {
        if (!row.valid()) {
            return Mono.just(new RowResult(
                    row.row(), row.userId(), row.amount(), STATUS_FAILED, null, row.errorCode(), row.errorMessage()));
        }
        var requestId = row.requestId() == null ? derivedRequestId(batchId, row) : row.requestId();
        return walletService
                .adjust(requestId, row.userId(), row.amount())
                .map(detail -> new RowResult(
                        row.row(), row.userId(), row.amount(), STATUS_SUCCESS, detail.balance(), null, null))
                .onErrorResume(ApiException.class, error -> Mono.just(new RowResult(
                        row.row(),
                        row.userId(),
                        row.amount(),
                        STATUS_FAILED,
                        null,
                        error.getCode(),
                        error.getReason())));
    }

    private static String derivedRequestId(String batchId, ParsedRow row) {
        var material = batchId + row.row() + row.userId() + row.amount().toPlainString();
        return IDEMPOTENCY_PREFIX + sha256(material);
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }

    private static BatchAdjustResult summarize(String batchId, List<RowResult> rows) {
        var succeeded = (int) rows.stream().filter(row -> STATUS_SUCCESS.equals(row.status())).count();
        return new BatchAdjustResult(batchId, rows.size(), succeeded, rows.size() - succeeded, rows);
    }

    private record ParsedRow(
            int row,
            @Nullable String userId,
            @Nullable BigDecimal amount,
            @Nullable String requestId,
            @Nullable String errorCode,
            @Nullable String errorMessage) {

        boolean valid() {
            return errorCode == null;
        }
    }

    public record BatchAdjustResult(
            String batchId,
            int total,
            int succeeded,
            int failed,
            List<RowResult> rows) {
    }

    public record RowResult(
            int row,
            @Nullable String userId,
            @Nullable BigDecimal amount,
            String status,
            @Nullable BigDecimal balance,
            @Nullable String errorCode,
            @Nullable String errorMessage) {
    }
}
