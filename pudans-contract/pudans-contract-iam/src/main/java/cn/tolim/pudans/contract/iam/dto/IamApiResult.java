package cn.tolim.pudans.contract.iam.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

// IAM API result envelope, aligned with cn.tolim.pudans.iam.infra.result.ApiRestResult
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamApiResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Whether the call succeeded
    private Boolean success;

    // Result status code, 200 on success
    private String status;

    // Result message
    private String message;

    // Result payload
    private T result;

    // Creates a success result
    // Parameter result: T
    // Returns: IamApiResult<T>
    public static <T> IamApiResult<T> ok(T result) {
        return IamApiResult.<T>builder()
                .success(Boolean.TRUE)
                .status("200")
                .message("success")
                .result(result)
                .build();
    }

    // Creates an error result
    // Parameter status: String
    // Parameter message: String
    // Returns: IamApiResult<T>
    public static <T> IamApiResult<T> error(String status, String message) {
        return IamApiResult.<T>builder()
                .success(Boolean.FALSE)
                .status(status)
                .message(message)
                .build();
    }
}