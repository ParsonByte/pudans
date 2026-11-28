package cn.tolim.pudans.iam.authentication.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.util.Assert;

import static cn.tolim.pudans.iam.communal.constant.AuthnConstants.AUTHN_PATH;
import static cn.tolim.pudans.iam.infra.security.constant.SecurityConstants.LOGIN_PATH;


public final class IdentityProviderType {

    // Feishu
    public static final IdentityProviderType FEISHU_OAUTH = new IdentityProviderType(
            "feishu_oauth", "飞书认证", "通过飞书进行身份验证");

    // DingTalk
    public static final IdentityProviderType DINGTALK_OAUTH = new IdentityProviderType(
            "dingtalk_oauth", "钉钉认证", "通过钉钉进行身份认证");

    // WeChat Open Platform
    public static final IdentityProviderType WECHAT = new IdentityProviderType(
            "wechat_oauth", "微信扫码登录", "通过微信扫码进行身份认证");
    // WeCom
    public static final IdentityProviderType WECHAT_WORK_OAUTH = new IdentityProviderType(
            "wechatwork_oauth", "企业微信认证", "通过企业微信同步的用户可使用企业微信扫码登录进行身份认证");

    // Gitee
    public static final IdentityProviderType GITEE_OAUTH = new IdentityProviderType(
            "gitee_oauth", "Gitee", "通过Gitee进行身份认证");

    // QQ authentication
    public static final IdentityProviderType QQ_OAUTH = new IdentityProviderType(
            "qq_oauth", "QQ认证", "通过QQ进行身份认证");

    // Username and password
    public static final IdentityProviderType USERNAME_PASSWORD = new IdentityProviderType(
            "username_password", "用户名密码认证", "通过用户名密码进行身份认证");

    // SMS verification code
    public static final IdentityProviderType SMS = new IdentityProviderType("sms",
            "短信验证码认证", "通过短信验证码进行身份认证");

    // Email verification code
    public static final IdentityProviderType MAIL = new IdentityProviderType("mail",
            "邮件验证码认证", "通过邮件验证码进行身份认证");

    // GitHub authentication
    public static final IdentityProviderType GITHUB_OAUTH = new IdentityProviderType(
            "github_oauth", "GITHUB认证", "通过GITHUB进行身份认证");

    // Alipay authentication
    public static final IdentityProviderType ALIPAY_OAUTH = new IdentityProviderType(
            "alipay_oauth", "支付宝认证", "通过支付宝进行身份认证");

    private final String value;
    private final String name;
    private final String desc;

    // Constructs an IdentityProviderType using the provided value.
    public IdentityProviderType(String value, String name, String desc) {
        Assert.hasText(value, "值不能为空");
        this.value = value;
        this.name = name;
        this.desc = desc;
    }

    // getIdentityProviderType
    public static IdentityProviderType getIdentityProviderType(String type) {
        if (FEISHU_OAUTH.value().equals(type)) {
            return FEISHU_OAUTH;
        }
        if (DINGTALK_OAUTH.value().equals(type)) {
            return DINGTALK_OAUTH;
        }
        if (WECHAT.value().equals(type)) {
            return WECHAT;
        }
        if (WECHAT_WORK_OAUTH.value().equals(type)) {
            return WECHAT_WORK_OAUTH;
        }
        if (QQ_OAUTH.value().equals(type)) {
            return QQ_OAUTH;
        }
        if (GITHUB_OAUTH.value().equals(type)) {
            return GITHUB_OAUTH;
        }
        if (GITEE_OAUTH.value().equals(type)) {
            return GITEE_OAUTH;
        }
        if (ALIPAY_OAUTH.value().equals(type)) {
            return ALIPAY_OAUTH;
        }
        if (USERNAME_PASSWORD.value().equals(type)) {
            return USERNAME_PASSWORD;
        }
        if (MAIL.value().equals(type)) {
            return MAIL;
        }
        if (SMS.value().equals(type)) {
            return SMS;
        }
        throw new IllegalArgumentException("未知身份提供商类型");
    }

    public static int size() {
        return 9;
    }

    // Returns the value of the authorization grant type.
    public String value() {
        return this.value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }
        IdentityProviderType that = (IdentityProviderType) obj;
        return this.value().equals(that.value());
    }

    @Override
    public int hashCode() {
        return this.value().hashCode();
    }

    public String name() {
        return name;
    }

    public String desc() {
        return desc;
    }

    @JsonIgnore
    public String getLoginPathPrefix() {
        return LOGIN_PATH + "/" + value();
    }

    @JsonIgnore
    public String getAuthorizationPathPrefix() {
        return AUTHN_PATH + "/" + value();
    }

    @Override
    public String toString() {
        return "IdentityProviderType[" + "value=" + value + ", " + "name=" + name + ", " + "desc="
                + desc + ']';
    }

}
