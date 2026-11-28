package cn.tolim.pudans.contract.iam.dto.app;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Application registration summary
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Application code
    private String appCode;

    // Application name
    private String name;

    // Application base URL
    private String baseUrl;

    // Ticket callback URL
    private String redirectUrl;

    // Back-channel logout notification URL
    private String backchannelLogoutUri;

    // Integration protocol: TICKET, JWT or OIDC
    private String protocol;

    // Application type
    private String type;

    // Icon URL
    private String icon;

    // Whether the application is enabled
    private Boolean enabled;

    // Whether the application configuration is complete
    private Boolean configured;

    // SSO ticket time to live in seconds
    private Long ticketTimeToLiveSeconds;

    // Application access policy mode
    private String accessPolicyMode;

    // Declared service actions
    private List<ServiceActionDefinition> actions;

    // Declared resource types
    private List<ResourceTypeDefinition> resourceTypes;
}
