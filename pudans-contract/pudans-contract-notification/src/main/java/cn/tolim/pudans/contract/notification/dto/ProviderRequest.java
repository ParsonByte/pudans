package cn.tolim.pudans.contract.notification.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Create or update a notification provider
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Provider code
    private String code;

    // Provider name
    private String name;

    // Channel
    private String channel;

    // Provider type
    private String provider;

    // Selection priority
    private Integer priority;

    // Whether the provider is enabled
    private Boolean enabled;

    // Provider configuration
    private Map<String, String> configuration;
}
