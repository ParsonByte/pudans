package cn.tolim.pudans.contract.iam.dto.ticket;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// SSO ticket issue result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TicketIssueResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // One-time ticket
    private String ticket;

    // Application code
    private String appCode;

    // Target URL the application should return to
    private String targetUrl;

    // Nonce bound to the ticket
    private String nonce;

    // Ticket expiration time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
}
