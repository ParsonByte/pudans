package cn.tolim.pudans.iam.protocol.cas.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CasServiceTicket implements Serializable {

    @Serial
    private static final long serialVersionUID = -4751559392057983471L;

    // Ticket ID
    private String id;

    // Ticket granting ticket ID
    private String ticketGrantingTicketId;

    // Application code
    private String appCode;

    // Service URL the ticket was issued for
    private String serviceUrl;

    // User ID
    private String userId;

    // Username
    private String username;

    // User attributes
    private Map<String, String> attributes;

    // Issued at
    private Instant issuedAt;

    // Expires at
    private Instant expiresAt;

    // Renew authentication requested
    private Boolean renew;

    public boolean isExpired() {
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }
}