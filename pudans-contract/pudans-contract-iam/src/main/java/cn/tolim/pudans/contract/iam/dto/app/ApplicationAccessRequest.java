package cn.tolim.pudans.contract.iam.dto.app;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Application access check request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationAccessRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // User ID
    private String userId;

    // Organization ID
    private String orgId;

    // User type: USER or ADMIN
    private String userType;
}
