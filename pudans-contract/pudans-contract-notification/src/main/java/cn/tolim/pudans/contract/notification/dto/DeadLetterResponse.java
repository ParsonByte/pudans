package cn.tolim.pudans.contract.notification.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Notification dead letter summary
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeadLetterResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Dead letter record ID
    private String id;

    // Original send record ID
    private String recordId;

    // Idempotency request ID
    private String requestId;

    // Notification template code
    private String templateCode;

    // Channel
    private String channel;

    // Provider code
    private String providerCode;

    // Provider type
    private String providerType;

    // Recipient
    private String recipient;

    // Subject
    private String subject;

    // Delivery attempts
    private Integer attempts;

    // Last delivery error
    private String lastError;

    // Failure time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime failedAt;

    // Whether the dead letter was replayed
    private Boolean replayed;

    // Replay time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime replayedAt;

    // Creation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
