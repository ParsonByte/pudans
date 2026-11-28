package cn.tolim.pudans.contract.iam.dto.auth;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Login page configuration
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginConfigResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Application code the configuration applies to
    private String appCode;

    // Whether username and password login is enabled
    private Boolean passwordLoginEnabled;

    // Whether captcha is required
    private Boolean captchaEnabled;

    // Whether MFA is required
    private Boolean mfaEnabled;

    // Enabled identity providers
    private List<IdentityProviderSummary> identityProviders;
}
