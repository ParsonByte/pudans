package cn.tolim.pudans.iam.core.auth.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;

public class TicketInvalidException extends PudansException {

    public TicketInvalidException(String message) {
        super("ticket_invalid", message, HttpStatus.BAD_REQUEST);
    }
}