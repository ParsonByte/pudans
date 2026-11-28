package cn.tolim.pudans.contract.notification.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Notification send record state
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SendNotificationResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Send record ID
    private String id;

    // Idempotency request ID
    private String requestId;

    // Notification template code
    private String templateCode;

    // Channel: EMAIL, SMS, FEISHU or DINGTALK
    private String channel;

    // Provider code
    private String providerCode;

    // Provider type: SMTP, ALIYUN_SMS, TENCENT_SMS, QINIU_SMS, FEISHU or DINGTALK
    private String providerType;

    // Recipient
    private String recipient;

    // Subject
    private String subject;

    // Send status: PENDING, SUCCESS, FAILED or DEAD_LETTER
    private String status;

    // Delivery attempts
    private Integer attempts;

    // Maximum delivery attempts
    private Integer maxAttempts;

    // Next retry time in ISO-8601, as returned by the notification service
    private LocalDateTime nextRetryAt;

    // Provider message ID
    private String providerMessageId;

    // Last delivery error
    private String lastError;

    // Send time in ISO-8601, as returned by the notification service
    private LocalDateTime sentAt;

    // Creation time in ISO-8601, as returned by the notification service
    private LocalDateTime createTime;
}
