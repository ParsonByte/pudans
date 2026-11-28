package cn.tolim.pudans.contract.iam.dto.auth;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// One-time password delivery request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OtpSendRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Email address or phone number
    private String recipient;

    // Delivery channel: mail or sms
    private String channel;
}