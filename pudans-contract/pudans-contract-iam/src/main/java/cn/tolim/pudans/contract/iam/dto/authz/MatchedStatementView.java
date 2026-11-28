package cn.tolim.pudans.contract.iam.dto.authz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

// Policy statement that contributed to a simulation result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchedStatementView implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Code of the policy that contains the statement
    private String policyCode;

    // Statement identifier
    private String sid;

    // Statement effect: Allow or Deny
    private String effect;
}