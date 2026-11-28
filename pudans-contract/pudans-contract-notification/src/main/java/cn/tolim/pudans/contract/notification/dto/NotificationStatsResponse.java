package cn.tolim.pudans.contract.notification.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Notification send statistics
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationStatsResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Send record counts by status
    private Map<String, Long> sendStatusCounts;

    // Number of dead letters not replayed
    private Long pendingDeadLetters;
}
