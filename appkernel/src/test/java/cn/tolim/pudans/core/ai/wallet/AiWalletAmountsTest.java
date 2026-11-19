package cn.tolim.pudans.core.ai.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.infra.exception.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AiWalletAmountsTest {

    @Test
    void chargedCostCapsAtTheHoldAmount() {
        assertThat(AiWalletAmounts.chargedCost(new BigDecimal("25.00"), new BigDecimal("10.00")))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void chargedCostKeepsTheActualCostWhenItFitsTheHold() {
        assertThat(AiWalletAmounts.chargedCost(new BigDecimal("3.50"), new BigDecimal("10.00")))
                .isEqualByComparingTo("3.50");
    }

    @Test
    void chargedCostAcceptsAZeroCost() {
        assertThat(AiWalletAmounts.chargedCost(BigDecimal.ZERO, new BigDecimal("10.00")))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void availableSubtractsTheFrozenAmount() {
        assertThat(AiWalletAmounts.available(new BigDecimal("100.00"), new BigDecimal("30.00")))
                .isEqualByComparingTo("70.00");
    }

    @Test
    void rejectsANegativeAmount() {
        assertThatThrownBy(() -> AiWalletAmounts.requireNonNegative(new BigDecimal("-0.01"), "冻结金额"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("ai-wallet-amount-invalid");
                    assertThat(error.getReason()).contains("不能为负数");
                });
    }

    @Test
    void rejectsAMissingAmount() {
        assertThatThrownBy(() -> AiWalletAmounts.requireNonNegative(null, "冻结金额"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("ai-wallet-amount-invalid");
                    assertThat(error.getReason()).contains("不能为空");
                });
    }
}
