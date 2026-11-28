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

// Notification template summary
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TemplateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Template record ID
    private String id;

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

    // Creation time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // Last modification time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
