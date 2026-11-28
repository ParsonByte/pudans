package cn.tolim.pudans.contract.iam.dto.ticket;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import cn.tolim.pudans.contract.iam.dto.ticket.UserInfo;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// SSO ticket exchange result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TicketExchangeResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Global login session ID the application session cache must be bound to
    private String sid;

    // Application code
    private String appCode;

    // User ID (subject)
    private String userId;

    // Global login session expiration time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sessionExpiresAt;

    // User information and claims
    private UserInfo user;
}
