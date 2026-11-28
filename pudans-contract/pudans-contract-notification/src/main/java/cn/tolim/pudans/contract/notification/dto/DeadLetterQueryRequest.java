package cn.tolim.pudans.contract.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

// Notification dead letter query request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeadLetterQueryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Channel filter
    private String channel;

    // Replayed filter
    private Boolean replayed;

    // Page number starting at zero
    private Integer page;

    // Page size, maximum 200
    private Integer size;
}
