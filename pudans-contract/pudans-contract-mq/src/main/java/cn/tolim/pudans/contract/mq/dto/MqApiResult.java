package cn.tolim.pudans.contract.mq.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Envelope returned by every message center endpoint
@JsonIgnoreProperties(ignoreUnknown = true)
public record MqApiResult<T>(String code, String message, T data) {

    public static final String SUCCESS_CODE = "OK";

    // Whether the call completed successfully
    public boolean success() {
        return SUCCESS_CODE.equals(code);
    }
}