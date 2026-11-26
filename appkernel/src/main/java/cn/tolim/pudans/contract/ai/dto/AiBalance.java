package cn.tolim.pudans.contract.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiBalance(
        @JsonProperty("is_available") Boolean available,
        @JsonProperty("balance_infos") List<AiBalanceInfo> balances) {

    public AiBalance {
        balances = balances == null ? List.of() : List.copyOf(balances);
    }
}
