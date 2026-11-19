package cn.tolim.pudans.core.ai.wallet;

import cn.tolim.pudans.infra.exception.ApiException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public final class AiWalletAmounts {

    private static final String CODE_AMOUNT_INVALID = "ai-wallet-amount-invalid";

    private AiWalletAmounts() {
    }

    public static BigDecimal requireNonNegative(BigDecimal value, String label) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CODE_AMOUNT_INVALID, label + "不能为空。");
        }
        if (value.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CODE_AMOUNT_INVALID, label + "不能为负数。");
        }
        return value;
    }

    public static BigDecimal chargedCost(BigDecimal actualCost, BigDecimal holdAmount) {
        return requireNonNegative(actualCost, "实际费用").min(requireNonNegative(holdAmount, "冻结金额"));
    }

    public static BigDecimal available(BigDecimal balance, BigDecimal frozen) {
        return balance.subtract(frozen);
    }
}
