package cn.tolim.pudans.contract.notification.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Notification provider summary
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Provider record ID
    private String id;

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

    // Provider configuration with secrets masked
    private Map<String, String> configuration;

    // Creation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // Last modification time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
