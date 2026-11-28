package cn.tolim.pudans.iam.communal.entity.setting.config;

import cn.tolim.pudans.iam.communal.enums.SmsType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

// SMS configuration
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SmsConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 5293005308937620292L;

    // Template configuration
    private List<TemplateConfig> templates;

    public SmsConfig() {
    }

    @Data
    public static class TemplateConfig implements Serializable {

        @Serial
        private static final long serialVersionUID = 2801844583775238689L;

        @Parameter(description = "短信类型")
        private SmsType type;
        @Parameter(description = "模板ID/CODE")
        private String code;

        public TemplateConfig() {
        }
    }
}