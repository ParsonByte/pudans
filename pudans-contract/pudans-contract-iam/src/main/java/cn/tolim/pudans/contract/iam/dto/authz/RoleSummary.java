package cn.tolim.pudans.contract.iam.dto.authz;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Role summary
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoleSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Role ID
    private String id;

    // Role code
    private String code;

    // Role name
    private String name;

    // Role status: ENABLED or DISABLED
    private String status;

    // Role description
    private String description;
}
