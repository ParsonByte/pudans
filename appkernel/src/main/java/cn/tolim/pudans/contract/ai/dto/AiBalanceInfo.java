package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiBalanceInfo(
        String currency,
        @JsonProperty("total_balance") String totalBalance,
        @JsonProperty("granted_balance") String grantedBalance,
        @JsonProperty("topped_up_balance") String toppedUpBalance) {}
