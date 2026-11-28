package cn.tolim.pudans.iam.protocol.saml.pojo;

import cn.tolim.pudans.iam.protocol.saml.constant.SamlBindingType;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlNameIdFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;


@Data
@Schema(description = "保存 SAML 应用配置")
public class AppSamlSaveConfigParam implements Serializable {

    @Serial
    private static final long serialVersionUID = 4498351873028514370L;

    // Login initiation URL
    @Schema(description = "登录发起地址")
    private String initLoginUrl;

    // Service provider entity ID
    @NotBlank(message = "SP entity ID must not be blank")
    @Schema(description = "SP 实体ID")
    private String entityId;

    // Assertion consumer service URL
    @NotBlank(message = "Assertion consumer service URL must not be blank")
    @URL(message = "Assertion consumer service URL is invalid")
    @Schema(description = "断言消费服务地址")
    private String assertionConsumerServiceUrl;

    // Single logout service URL
    @URL(message = "Single logout service URL is invalid")
    @Schema(description = "单点登出服务地址")
    private String singleLogoutServiceUrl;

    // Name id format
    @Schema(description = "名称ID格式")
    private SamlNameIdFormat nameIdFormat = SamlNameIdFormat.PERSISTENT;

    // Default response binding
    @Schema(description = "默认响应绑定")
    private SamlBindingType defaultBinding = SamlBindingType.POST;

    // Assertion lifetime in seconds
    @Min(value = 60, message = "Assertion lifetime must be at least 60 seconds")
    @Max(value = 86400, message = "Assertion lifetime must be at most 86400 seconds")
    @Schema(description = "断言有效期（秒）")
    private Integer assertionTimeToLive;

    // Allowed clock skew in seconds
    @Min(value = 0, message = "Clock skew must not be negative")
    @Max(value = 600, message = "Clock skew must be at most 600 seconds")
    @Schema(description = "允许的时钟偏移（秒）")
    private Integer clockSkew;

    // Require signed assertions
    @Schema(description = "要求签名断言")
    private Boolean wantAssertionsSigned = true;

    // Require signed authn requests
    @Schema(description = "要求签名认证请求")
    private Boolean wantAuthnRequestsSigned = false;

    // Signing algorithm
    @Schema(description = "签名算法")
    private String signingAlgorithm;

    // Attribute name to user field mappings
    @Schema(description = "属性名到用户字段的映射")
    private Map<String, String> attributeMappings;
}