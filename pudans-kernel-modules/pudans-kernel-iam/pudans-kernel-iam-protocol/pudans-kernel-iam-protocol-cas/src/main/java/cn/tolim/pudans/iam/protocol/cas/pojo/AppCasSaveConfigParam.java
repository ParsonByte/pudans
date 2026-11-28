package cn.tolim.pudans.iam.protocol.cas.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;


@Data
@Schema(description = "保存 CAS 应用配置")
public class AppCasSaveConfigParam implements Serializable {

    @Serial
    private static final long serialVersionUID = -1779658037073172358L;

    // Login initiation URL
    @Schema(description = "登录发起地址")
    private String initLoginUrl;

    // Registered service URLs
    @NotEmpty(message = "At least one service URL is required")
    @Schema(description = "已注册服务地址")
    private List<@NotBlank(message = "Service URL must not be blank")
            @URL(message = "Service URL is invalid") String> serviceUrls;

    // Default service URL
    @URL(message = "Default service URL is invalid")
    @Schema(description = "默认服务地址")
    private String defaultServiceUrl;

    // Service ticket lifetime in seconds
    @Min(value = 1, message = "Ticket lifetime must be at least 1 second")
    @Max(value = 600, message = "Ticket lifetime must be at most 600 seconds")
    @Schema(description = "服务票据有效期（秒）")
    private Integer ticketTimeToLive;

    // Ticket granting ticket lifetime in seconds
    @Min(value = 60, message = "Ticket granting ticket lifetime must be at least 60 seconds")
    @Max(value = 604800,
            message = "Ticket granting ticket lifetime must be at most 604800 seconds")
    @Schema(description = "票据授予票据有效期（秒）")
    private Integer ticketGrantingTicketTimeToLive;

    // Allow renew authentication
    @Schema(description = "允许续期认证")
    private Boolean renewEnabled = false;

    // Mark the ticket cookie as secure
    @Schema(description = "将票据 Cookie 标记为安全")
    private Boolean secureCookie = true;

    // Ticket granting ticket cookie name
    @Schema(description = "票据授予票据 Cookie 名称")
    private String cookieName;

    // Attribute name to user field mappings
    @Schema(description = "属性名到用户字段的映射")
    private Map<String, String> attributeMappings;
}