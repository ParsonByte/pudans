package cn.tolim.pudans.iam.protocol.cas.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CasConfigData implements Serializable {

    @Serial
    private static final long serialVersionUID = -3189554019327056627L;

    // Registered service URLs
    private Set<String> serviceUrls;

    // Service ticket lifetime in seconds
    private Long ticketTimeToLiveSeconds;

    // Ticket granting ticket lifetime in seconds
    private Long ticketGrantingTicketTimeToLiveSeconds;

    // Allow renew authentication
    private Boolean renewEnabled;

    // Mark the ticket cookie as secure
    private Boolean secureCookie;

    // Ticket granting ticket cookie name
    private String cookieName;

    // Default service URL
    private String defaultServiceUrl;

    // Attribute name to user field mappings
    private Map<String, String> attributeMappings;
}