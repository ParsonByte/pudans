package cn.tolim.pudans.iam.core.auth.otp;

import cn.tolim.pudans.iam.communal.enums.MailType;
import cn.tolim.pudans.iam.communal.enums.MessageNoticeChannel;
import cn.tolim.pudans.iam.communal.enums.SmsType;
import cn.tolim.pudans.iam.core.auth.exception.InvalidAuthRequestException;
import cn.tolim.pudans.iam.infra.util.PhoneUtils;
import org.apache.commons.lang3.StringUtils;

// Delivery channel of a login or password reset verification code
public enum OtpChannel {

    MAIL,
    SMS;

    public static OtpChannel from(String value) {
        if ("mail".equalsIgnoreCase(StringUtils.trimToEmpty(value))) {
            return MAIL;
        }
        if ("sms".equalsIgnoreCase(StringUtils.trimToEmpty(value))) {
            return SMS;
        }
        throw new InvalidAuthRequestException("otp_channel_invalid",
                "The verification channel must be mail or sms");
    }

    public MessageNoticeChannel noticeChannel() {
        return this == MAIL ? MessageNoticeChannel.MAIL : MessageNoticeChannel.SMS;
    }

    public String loginType() {
        return this == MAIL ? MailType.LOGIN.getCode() : SmsType.LOGIN.getCode();
    }

    public String passwordResetType() {
        return this == MAIL ? MailType.FORGET_PASSWORD.getCode()
                : SmsType.FORGET_PASSWORD.getCode();
    }

    public String normalize(String recipient) {
        String value = StringUtils.trimToNull(recipient);
        if (value == null) {
            return null;
        }
        return this == SMS ? PhoneUtils.getPhoneNumber(value) : value;
    }
}