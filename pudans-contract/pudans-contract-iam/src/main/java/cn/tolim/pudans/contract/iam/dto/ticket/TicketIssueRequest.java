package cn.tolim.pudans.contract.iam.dto.ticket;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// SSO ticket issue request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TicketIssueRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Global login session ID of the current user
    private String sid;

    // Application code the ticket is issued for
    private String appCode;

    // Target URL the application should return to after ticket exchange
    private String targetUrl;

    // Optional nonce bound to the ticket
    private String nonce;
}
