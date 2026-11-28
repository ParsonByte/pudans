package cn.tolim.pudans.iam.protocol.cas.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;


@Data
@Schema(description = "CAS 应用配置")
public class AppCasConfigGetResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 469549178747415626L;

    // Application ID
    @Schema(description = "应用ID")
    private String appId;

    // Login initiation URL
    @Schema(description = "登录发起地址")
    private String initLoginUrl;

    // Registered service URLs
    @Schema(description = "已注册服务地址")
    private Set<String> serviceUrls;

    // Default service URL
    @Schema(description = "默认服务地址")
    private String defaultServiceUrl;

    // Service ticket lifetime in seconds
    @Schema(description = "服务票据有效期（秒）")
    private String ticketTimeToLive;

    // Ticket granting ticket lifetime in seconds
    @Schema(description = "票据授予票据有效期（秒）")
    private String ticketGrantingTicketTimeToLive;

    // Allow renew authentication
    @Schema(description = "允许续期认证")
    private Boolean renewEnabled;

    // Mark the ticket cookie as secure
    @Schema(description = "将票据 Cookie 标记为安全")
    private Boolean secureCookie;

    // Ticket granting ticket cookie name
    @Schema(description = "票据授予票据 Cookie 名称")
    private String cookieName;

    // Attribute name to user field mappings
    @Schema(description = "属性名到用户字段的映射")
    private Map<String, String> attributeMappings;

    // Protocol endpoints
    @Schema(description = "协议端点")
    private AppCasProtocolEndpoint protocolEndpoint;
}