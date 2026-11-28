package cn.tolim.pudans.contract.iam.dto.authz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

// Effective roles and policies of a principal
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EffectivePermissionResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Principal type
    private String principalType;

    // Principal ID
    private String principalId;

    // Roles bound to the principal
    private List<RoleSummary> roles;

    // Effective policies of the principal
    private List<EffectivePolicyView> policies;

    // Effective policy codes
    private List<String> policyCodes;
}