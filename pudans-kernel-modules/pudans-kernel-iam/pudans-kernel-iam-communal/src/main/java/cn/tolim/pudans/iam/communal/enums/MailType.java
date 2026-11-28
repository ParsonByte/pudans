package cn.tolim.pudans.iam.communal.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum MailType implements BaseEnum {

    // Bind email
    BIND_EMAIL("bind_email", "绑定邮箱", "每当用户绑定邮箱时，都会发送此电子邮件。",
            "您正在绑定邮箱，验证码为：${verify_code}", "系统账户",
            getMailContentPath() + "bind-mail-content.html"),
    // Change binding email
    UPDATE_BIND_MAIL("update_bind_mail", "修改绑定邮件",
            "每当用户需要修改绑定邮箱、发送验证码时，都会发送此电子邮件。",
            "您正在修改绑定邮箱，验证码为：${verify_code}", "系统账户",
            getMailContentPath() + "update-bind-mail-content.html"),

    // Forgot password
    FORGET_PASSWORD("forget_password", "忘记密码",
            "每当用户忘记密码时，都会发送此电子邮件。",
            "您正在修改密码，验证码为：${verify_code}", "系统账户",
            getMailContentPath() + "update-password-content.html"),
    // Change password
    UPDATE_PASSWORD("update_password", "修改密码",
            "每当用户要求更改密码时，都会发送此电子邮件。",
            "您正在修改密码，验证码为：${verify_code}", "系统账户",
            getMailContentPath() + "update-password-content.html"),
    // Reset password
    RESET_PASSWORD("reset_password", "重置密码",
            "每当用户要求更改密码时，都会发送此电子邮件。",
            "您正在重置密码，验证码为：${verify_code}", "系统账户",
            getMailContentPath() + "reset-password-content.html"),
    // Password reset successful
    RESET_PASSWORD_CONFIRM("reset_password_confirm", "重置密码成功",
            "每当用户重置密码成功时，都会发送此电子邮件。",
            "您已成功重置密码，您重置后的密码为：${password} 如非本人操作请立即修改账户密码",
            "系统账户", getMailContentPath()
            + "reset-password-confirm-content.html"),

    // Login verification
    LOGIN("login", "登录验证", "每当用户选择邮箱验证码登录时，都会发送此电子邮件。",
            "您正在登录PUDANS系统，验证码为：${verify_code}，该验证码${expire_time}分钟内有效，请勿泄露于他人。",
            "系统账户",
            getMailContentPath() + "login-content.html"),

    // Password expiration reminder
    PASSWORD_SOON_EXPIRED_REMIND("password_soon_expired_remind",
            "密码即将到期提醒",
            "用户距离上次修改密码有效期即将过期时，都会发送此电子邮件。",
            "您的密码将于${expire_days}天后过期, 请尽快修改密码。",
            "系统账户", getMailContentPath()
            + "password-soon-expired-content.html"),
    // Welcome email
    WELCOME_MAIL("welcome_mail", "欢迎邮件",
            "一旦用户验证其电子邮件地址，将发送此电子邮件。 如果验证电子邮件已关闭， 则会在用户第一次注册或登录时发送。",
            "您已注册成功", "系统账户", getMailContentPath()
            + "welcome-mail-content.html");

    // code
    @JsonValue
    private final String code;
    // Name
    private final String name;
    // desc
    private final String desc;
    // Subject
    private final String subject;
    // Sender
    private final String sender;
    // Content
    private final String content;

    MailType(String code, String name, String desc, String subject, String sender, String content) {
        this.code = code;
        this.name = name;
        this.desc = desc;
        this.subject = subject;
        this.sender = sender;
        this.content = content;
    }

    static String getMailContentPath() {
        return "mail/content/";
    }

    @EnumConvert
    public static MailType getType(String code) {
        MailType[] values = values();
        for (MailType status : values) {
            if (String.valueOf(status.getCode()).equals(code)) {
                return status;
            }
        }
        throw new NullPointerException("未找到该类型");
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    public String getContent() {
        return content;
    }

    public String getSubject() {
        return subject;
    }

    public String getName() {
        return name;
    }

    public String getSender() {
        return sender;
    }

    @Override
    public String toString() {
        return this.code;
    }

}
