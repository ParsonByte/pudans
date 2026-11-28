package cn.tolim.pudans.contract.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

// Notification send record query request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationRecordQueryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Send status filter: PENDING, SUCCESS, FAILED or DEAD_LETTER
    private String status;

    // Channel filter: EMAIL, SMS, FEISHU or DINGTALK
    private String channel;

    // Provider code filter
    private String providerCode;

    // Recipient filter
    private String recipient;

    // Idempotency request ID filter
    private String requestId;

    // Template code filter
    private String templateCode;

    // Page number starting at zero
    private Integer page;

    // Page size, maximum 200
    private Integer size;
}
