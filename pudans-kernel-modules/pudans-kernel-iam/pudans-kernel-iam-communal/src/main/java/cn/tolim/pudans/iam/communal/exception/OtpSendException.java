package cn.tolim.pudans.iam.communal.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;


public class OtpSendException extends PudansException {

    public OtpSendException(String message) {
        super("otp_send_error", message, DEFAULT_STATUS);
    }

}
