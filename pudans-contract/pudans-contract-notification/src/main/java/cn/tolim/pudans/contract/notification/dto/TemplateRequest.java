package cn.tolim.pudans.contract.notification.dto;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Create or replace a notification template
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TemplateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Template code
    private String code;

    // Template name
    private String name;

    // Channel
    private String channel;

    // Subject template
    private String subject;

    // Content template
    private String content;

    // Provider side template identifier
    private String providerTemplateCode;

    // Template language
    private String language;

    // Whether the template is enabled
    private Boolean enabled;
}
