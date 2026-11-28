package cn.tolim.pudans.contract.iam.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

// IAM API response envelope for application endpoints, aligned with
// cn.tolim.pudans.communal.core.contract.ApiResponse
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamApiResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Result code, OK on success
    private String code;

    // Result message
    private String message;

    // Result payload
    private T data;

    // Creates a success result
    // Parameter data: T
    // Returns: IamApiResponse<T>
    public static <T> IamApiResponse<T> ok(T data) {
        return IamApiResponse.<T>builder().code("OK").message("OK").data(data).build();
    }

    // Creates an error result
    // Parameter code: String
    // Parameter message: String
    // Returns: IamApiResponse<T>
    public static <T> IamApiResponse<T> error(String code, String message) {
        return IamApiResponse.<T>builder().code(code).message(message).build();
    }
}