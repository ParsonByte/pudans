package cn.tolim.pudans.iam.authorization.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import cn.tolim.pudans.iam.infra.exception.enums.ExceptionStatus;
import org.springframework.http.HttpStatus;


public final class AuthorizationErrors {

    private AuthorizationErrors() {
    }

    // Creates a bad request error for an invalid authorization payload
    // Parameter message: String
    // Returns: PudansException
    public static PudansException badRequest(String message) {
        return new PudansException(ExceptionStatus.EX000101.getCode(), message, HttpStatus.BAD_REQUEST);
    }

    // Creates a not found error for a missing authorization resource
    // Parameter message: String
    // Returns: PudansException
    public static PudansException notFound(String message) {
        return new PudansException(ExceptionStatus.EX000108.getCode(), message, HttpStatus.NOT_FOUND);
    }

    // Creates a conflict error for a duplicated authorization resource
    // Parameter message: String
    // Returns: PudansException
    public static PudansException conflict(String message) {
        return new PudansException(ExceptionStatus.EX000109.getCode(), message, HttpStatus.CONFLICT);
    }

    // Creates a forbidden error for a denied authorization operation
    // Parameter message: String
    // Returns: PudansException
    public static PudansException forbidden(String message) {
        return new PudansException(ExceptionStatus.EX000104.getCode(), message, HttpStatus.FORBIDDEN);
    }
}