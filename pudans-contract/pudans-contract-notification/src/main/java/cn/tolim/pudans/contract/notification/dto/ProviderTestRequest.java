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

// Provider connectivity test request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderTestRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Test recipient
    private String recipient;

    // Test subject
    private String subject;

    // Test content
    private String content;

    // Provider side template identifier
    private String providerTemplateCode;

    // Named template parameters
    private Map<String, String> parameters;

    // Ordered template parameters
    private List<String> templateParameters;
}
