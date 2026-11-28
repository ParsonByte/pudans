package cn.tolim.pudans.contract.notification.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Send one notification request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SendNotificationRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Recipient address, phone number or chat ID depending on the channel
    private String recipient;

    // Channel: EMAIL, SMS, FEISHU or DINGTALK
    private String channel;

    // Provider code, optional when provider selection is automatic
    private String providerCode;

    // Notification template code
    private String templateCode;

    // Provider side template identifier
    private String providerTemplateCode;

    // Subject
    private String subject;

    // Content
    private String content;

    // Named parameters used for content rendering and provider templates
    private Map<String, String> parameters;

    // Ordered parameters used by providers that require a list
    private List<String> templateParameters;

    // Idempotency request ID
    private String requestId;

    // Whether the notification should be sent asynchronously
    private Boolean async;

    // Maximum delivery attempts
    private Integer maxAttempts;
}
