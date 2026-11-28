package cn.tolim.pudans.iam.infra.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class PudansException extends RuntimeException {
    public static final String BIND_EXCEPTION = "bind_exception";
    public static final String DESCRIPTION = "error_description";
    public static final String STATUS = "status";
    public static final String DEFAULT_EXCEPTION = "unknown_exception";
    public static final String CONSTRAINT_VIOLATION_EXCEPTION = "constraint_violation_exception";
    public static final String ERROR = "error";
    public static final HttpStatus DEFAULT_STATUS = HttpStatus.INTERNAL_SERVER_ERROR;

    private final String errorCode;
    private final HttpStatus httpStatus;
    private Map<String, String> additionalInformation;

    public PudansException(String message) {
        this(DEFAULT_EXCEPTION, message, DEFAULT_STATUS);
    }

    public PudansException(String message, HttpStatus status) {
        this(DEFAULT_EXCEPTION, message, status);
    }

    public PudansException(String errorCode, String message) {
        this(errorCode, message, DEFAULT_STATUS);
    }

    public PudansException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = DEFAULT_EXCEPTION;
        this.httpStatus = DEFAULT_STATUS;
    }

    public PudansException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = status;
    }

    public PudansException(Throwable cause, String errorCode, String message, HttpStatus status) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = status;
    }

    public static PudansException valueOf(Map<String, String> details) {
        String error = details.get(ERROR);
        String description = details.getOrDefault(DESCRIPTION, null);
        HttpStatus status = DEFAULT_STATUS;
        if (details.containsKey(STATUS)) {
            try {
                status = HttpStatus.valueOf(Integer.parseInt(details.get(STATUS)));
            } catch (NumberFormatException ignored) {
                status = DEFAULT_STATUS;
            }
        }
        PudansException exception = new PudansException(error, description, status);
        details.forEach((key, value) -> {
            if (!ERROR.equals(key) && !DESCRIPTION.equals(key)) {
                exception.addAdditionalInformation(key, value);
            }
        });
        return exception;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public Map<String, String> getAdditionalInformation() {
        return additionalInformation == null ? Collections.emptyMap() : additionalInformation;
    }

    public void addAdditionalInformation(String key, String value) {
        if (additionalInformation == null) {
            additionalInformation = new TreeMap<>();
        }
        additionalInformation.put(key, value);
    }

    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        if (errorCode != null) {
            summary.append("错误=").append(errorCode);
        }
        if (getMessage() != null) {
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            summary.append("消息=").append(getMessage());
        }
        if (additionalInformation != null) {
            additionalInformation.forEach((key, value) -> summary.append(", ").append(key)
                    .append("=").append(value));
        }
        return summary.toString();
    }

    @Override
    public String toString() {
        return getSummary();
    }
}
