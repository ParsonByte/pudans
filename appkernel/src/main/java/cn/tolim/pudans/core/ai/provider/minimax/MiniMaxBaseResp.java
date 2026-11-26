package cn.tolim.pudans.core.ai.provider.minimax;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// MiniMax 旧版接口用状态码表达业务失败。
@JsonIgnoreProperties(ignoreUnknown = true)
record MiniMaxBaseResp(
        @JsonProperty("status_code") Integer statusCode,
        @JsonProperty("status_msg") String statusMsg) {}
