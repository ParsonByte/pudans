package cn.tolim.pudans.iam.protocol.cas.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;


@Data
@Builder
public class CasServiceValidationResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 8236002234838852422L;

    // Validation succeeded
    private boolean success;

    // Authenticated username
    private String username;

    // User attributes
    private Map<String, String> attributes;

    // Failure code
    private String failureCode;

    // Failure message
    private String failureMessage;

    // Build a successful result.
    public static CasServiceValidationResult success(String username,
                                                     Map<String, String> attributes) {
        return CasServiceValidationResult.builder()
                .success(true)
                .username(username)
                .attributes(attributes == null ? Map.of() : attributes)
                .build();
    }

    // Build a failed result.
    public static CasServiceValidationResult failure(String code, String message) {
        return CasServiceValidationResult.builder()
                .success(false)
                .failureCode(code)
                .failureMessage(message)
                .attributes(Map.of())
                .build();
    }
}