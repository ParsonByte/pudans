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
public class CasTicketGrantingTicket implements Serializable {

    @Serial
    private static final long serialVersionUID = 5903211376479822185L;

    // Ticket ID
    private String id;

    // Application code
    private String appCode;

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

    public boolean isExpired() {
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }
}