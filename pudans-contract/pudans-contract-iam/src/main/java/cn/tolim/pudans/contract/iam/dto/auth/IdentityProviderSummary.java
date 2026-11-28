package cn.tolim.pudans.contract.iam.dto.auth;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Configured identity provider summary
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentityProviderSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Identity provider code
    private String code;

    // Identity provider name
    private String name;

    // Identity provider type
    private String type;

    // Authentication protocol
    private String protocol;

    // Whether the identity provider is enabled
    private Boolean enabled;

    // Icon URL
    private String iconUrl;

    // Authorization URL for redirect based providers
    private String authorizeUrl;
}
