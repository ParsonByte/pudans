package cn.tolim.pudans.contract.iam.dto.app;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Controlled application self-registration request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationRegistrationRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Application code
    private String appCode;

    // Application name
    private String name;

    // Application base URL
    private String baseUrl;

    // Ticket callback path, for example /sso/callback
    private String callbackPath;

    // Back-channel logout path, for example /sso/backchannel-logout
    private String backchannelLogoutPath;

    // Integration protocol: TICKET, JWT or OIDC
    private String protocol;

    // Deployment environment
    private String environment;

    // Owning team
    private String owner;

    // Declared service actions
    private List<ServiceActionDefinition> actions;

    // Declared resource types
    private List<ResourceTypeDefinition> resourceTypes;
}
