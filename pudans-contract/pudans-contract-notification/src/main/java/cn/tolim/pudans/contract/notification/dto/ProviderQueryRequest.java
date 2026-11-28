package cn.tolim.pudans.contract.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

// Notification provider query request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderQueryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Channel filter
    private String channel;

    // Enabled filter
    private Boolean enabled;

    // Keyword matched against provider code and name
    private String keyword;

    // Page number starting at zero
    private Integer page;

    // Page size, maximum 200
    private Integer size;
}
