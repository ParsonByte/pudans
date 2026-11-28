package cn.tolim.pudans.contract.iam.dto.authz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

// Effective policy granted to a principal
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EffectivePolicyView implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Policy ID
    private String policyId;

    // Policy code
    private String policyCode;

    // Policy name
    private String policyName;

    // Application or resource scope the policy applies to
    private String scope;

    // Principal type that granted the policy
    private String sourcePrincipalType;

    // Principal ID that granted the policy
    private String sourcePrincipalId;
}