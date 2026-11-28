package cn.tolim.pudans.contract.repository.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

// Repository API result envelope, aligned with cn.tolim.pudans.communal.core.contract.ApiResponse
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RepositoryApiResult<T> implements Serializable {

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
    // Returns: RepositoryApiResult<T>
    public static <T> RepositoryApiResult<T> ok(T data) {
        return RepositoryApiResult.<T>builder().code("OK").message("OK").data(data).build();
    }

    // Creates an error result
    // Parameter code: String
    // Parameter message: String
    // Returns: RepositoryApiResult<T>
    public static <T> RepositoryApiResult<T> error(String code, String message) {
        return RepositoryApiResult.<T>builder().code(code).message(message).build();
    }
}
