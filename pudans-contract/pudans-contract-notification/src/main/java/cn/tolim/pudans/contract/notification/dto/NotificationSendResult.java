package cn.tolim.pudans.contract.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

// Result returned by a provider connectivity test
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationSendResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Whether the provider accepted the test message
    private Boolean success;

    // Provider message ID
    private String providerMessageId;

    // Provider response
    private String response;

    // Error message when the test failed
    private String error;
}
