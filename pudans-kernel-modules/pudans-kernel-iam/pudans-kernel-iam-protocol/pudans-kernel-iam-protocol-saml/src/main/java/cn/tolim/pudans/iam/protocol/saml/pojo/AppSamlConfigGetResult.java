package cn.tolim.pudans.iam.protocol.saml.pojo;

import cn.tolim.pudans.iam.protocol.saml.constant.SamlBindingType;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlNameIdFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;


@Data
@Schema(description = "SAML 应用配置")
public class AppSamlConfigGetResult implements Serializable {

    @Serial
    private static final long serialVersionUID = -3796568873564515967L;

    // Application ID
    @Schema(description = "应用ID")
    private String appId;

    // Login initiation URL
    @Schema(description = "登录发起地址")
    private String initLoginUrl;

    // Service provider entity ID
    @Schema(description = "SP 实体ID")
    private String entityId;

    // Assertion consumer service URL
    @Schema(description = "断言消费服务地址")
    private String assertionConsumerServiceUrl;

    // Single logout service URL
    @Schema(description = "单点登出服务地址")
    private String singleLogoutServiceUrl;

    // Name id format
    @Schema(description = "名称ID格式")
    private SamlNameIdFormat nameIdFormat;

    // Default response binding
    @Schema(description = "默认响应绑定")
    private SamlBindingType defaultBinding;

    // Assertion lifetime in seconds
    @Schema(description = "断言有效期（秒）")
    private String assertionTimeToLive;

    // Allowed clock skew in seconds
    @Schema(description = "允许的时钟偏移（秒）")
    private String clockSkew;

    // Require signed assertions
    @Schema(description = "要求签名断言")
    private Boolean wantAssertionsSigned;

    // Require signed authn requests
    @Schema(description = "要求签名认证请求")
    private Boolean wantAuthnRequestsSigned;

    // Signing algorithm
    @Schema(description = "签名算法")
    private String signingAlgorithm;

    // Attribute name to user field mappings
    @Schema(description = "属性名到用户字段的映射")
    private Map<String, String> attributeMappings;

    // Protocol endpoints
    @Schema(description = "协议端点")
    private AppSamlProtocolEndpoint protocolEndpoint;
}