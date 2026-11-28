package cn.tolim.pudans.contract.audit.dto;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Audit user agent
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditUserAgent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Device type
    private String deviceType;

    // Platform
    private String platform;

    // Platform version
    private String platformVersion;

    // Browser
    private String browser;

    // Browser type
    private String browserType;

    // Browser major version
    private String browserMajorVersion;
}
