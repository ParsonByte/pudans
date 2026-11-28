package cn.tolim.pudans.contract.iam.dto.ticket;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// SSO ticket exchange request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TicketExchangeRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // One-time ticket
    private String ticket;

    // Application code that exchanges the ticket
    private String appCode;

    // Application client ID
    private String clientId;

    // Application client secret
    private String clientSecret;
}
