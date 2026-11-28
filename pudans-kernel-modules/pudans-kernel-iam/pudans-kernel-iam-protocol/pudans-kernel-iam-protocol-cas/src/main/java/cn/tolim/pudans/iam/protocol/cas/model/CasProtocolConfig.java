package cn.tolim.pudans.iam.protocol.cas.model;

import cn.tolim.pudans.iam.appkernel.core.AbstractProtocolConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.DEFAULT_COOKIE_NAME;
import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.DEFAULT_TICKET_GRANTING_TICKET_TIME_TO_LIVE_SECONDS;
import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.DEFAULT_TICKET_TIME_TO_LIVE_SECONDS;


@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class CasProtocolConfig extends AbstractProtocolConfig {

    @Serial
    private static final long serialVersionUID = 2731589447623411650L;

    // Registered service URLs
    private Set<String> serviceUrls;

    // Service ticket lifetime
    private Duration ticketTimeToLive;

    // Ticket granting ticket lifetime
    private Duration ticketGrantingTicketTimeToLive;

    // Allow renew authentication
    private Boolean renewEnabled;

    // Mark the ticket cookie as secure
    private Boolean secureCookie;

    // Ticket granting ticket cookie name
    private String cookieName;

    // Default service URL used when the login request does not carry one
    private String defaultServiceUrl;

    // Attribute name to user field mappings
    private Map<String, String> attributeMappings;

    // Configured
    private Boolean configured;

    public Set<String> getServiceUrls() {
        return serviceUrls == null ? Set.of() : serviceUrls;
    }

    public Duration getTicketTimeToLive() {
        return Objects.requireNonNullElse(ticketTimeToLive,
                Duration.ofSeconds(DEFAULT_TICKET_TIME_TO_LIVE_SECONDS));
    }

    public Duration getTicketGrantingTicketTimeToLive() {
        return Objects.requireNonNullElse(ticketGrantingTicketTimeToLive,
                Duration.ofSeconds(DEFAULT_TICKET_GRANTING_TICKET_TIME_TO_LIVE_SECONDS));
    }

    public Boolean getRenewEnabled() {
        return Objects.requireNonNullElse(renewEnabled, false);
    }

    public Boolean getSecureCookie() {
        return Objects.requireNonNullElse(secureCookie, true);
    }

    public String getCookieName() {
        return Objects.requireNonNullElse(cookieName, DEFAULT_COOKIE_NAME);
    }

    public Map<String, String> getAttributeMappings() {
        return attributeMappings == null ? Map.of() : attributeMappings;
    }

    public Boolean getConfigured() {
        return Objects.requireNonNullElse(configured, false);
    }
}