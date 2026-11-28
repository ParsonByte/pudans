package cn.tolim.pudans.contract.job.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Envelope returned by every job executor endpoint
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobApiResult<T>(String code, String message, T data) {

    public static final String SUCCESS_CODE = "OK";

    // Whether the call completed successfully
    public boolean success() {
        return SUCCESS_CODE.equals(code);
    }
}