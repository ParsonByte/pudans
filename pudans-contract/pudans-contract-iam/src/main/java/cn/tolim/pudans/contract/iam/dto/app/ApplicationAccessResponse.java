package cn.tolim.pudans.contract.iam.dto.app;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Application access check result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationAccessResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Whether the user may access the application
    private Boolean allowed;

    // Application code
    private String appCode;

    // User ID
    private String userId;

    // Organization ID
    private String orgId;

    // Decision reason
    private String reason;
}
