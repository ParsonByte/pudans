package cn.tolim.pudans.iam.infra.result;

import cn.tolim.pudans.iam.infra.exception.enums.ExceptionStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.io.Serializable;

@Schema(description = "API 响应结果")
public class ApiRestResult<T> implements Serializable {
    public static final String FAIL = "fail";
    public static final String SUCCESS = "success";

    @JsonProperty("success")
    private final Boolean success;
    @JsonProperty("status")
    private String status;
    @JsonProperty("message")
    private String message;
    @JsonProperty("result")
    private T result;

    public ApiRestResult(String status, String message, T result) {
        this.status = status;
        this.message = message;
        this.result = result;
        this.success = String.valueOf(HttpStatus.OK.value()).equals(status);
    }

    public static <T> ApiRestResult<T> ok() {
        return ok(null);
    }

    public static <T> ApiRestResult<T> ok(T result) {
        return new ApiRestResult<>(String.valueOf(HttpStatus.OK.value()), SUCCESS, result);
    }

    public static <T> ApiRestResult<T> err() {
        return err(ExceptionStatus.EX900001.getMessage(), ExceptionStatus.EX900001.getCode());
    }

    public static <T> ApiRestResult<T> err(T result) {
        return new ApiRestResult<>(ExceptionStatus.EX900001.getCode(),
                ExceptionStatus.EX900001.getMessage(), result);
    }

    public static <T> ApiRestResult<T> err(String message, String status) {
        return new ApiRestResult<>(status, message, null);
    }

    public static <T> RestResultBuilder<T> builder() {
        return new RestResultBuilder<>();
    }

    public T getResult() {
        return result;
    }

    public String getStatus() {
        return status == null || status.isBlank() ? String.valueOf(HttpStatus.OK.value()) : status;
    }

    public String getMessage() {
        if (message == null || message.isBlank()) {
            return Boolean.TRUE.equals(success) ? SUCCESS : FAIL;
        }
        return message;
    }

    public Boolean getSuccess() {
        return success;
    }

    public ApiRestResult<T> status(String status) {
        this.status = status;
        return this;
    }

    public ApiRestResult<T> message(String message) {
        this.message = message;
        return this;
    }

    public ApiRestResult<T> result(T result) {
        this.result = result;
        return this;
    }

    public ApiRestResult<T> build() {
        return new ApiRestResult<>(status, message, result);
    }

    public static class RestResultBuilder<T> {
        private String status;
        private String message;
        private T result;

        public RestResultBuilder<T> status(String status) {
            this.status = status;
            return this;
        }

        public RestResultBuilder<T> message(String message) {
            this.message = message;
            return this;
        }

        public RestResultBuilder<T> result(T result) {
            this.result = result;
            return this;
        }

        public ApiRestResult<T> build() {
            String resolvedStatus = status == null || status.isBlank()
                    ? String.valueOf(HttpStatus.OK.value()) : status;
            String resolvedMessage = message;
            if (resolvedMessage == null || resolvedMessage.isBlank()) {
                resolvedMessage = String.valueOf(HttpStatus.OK.value()).equals(resolvedStatus)
                        ? SUCCESS : FAIL;
            }
            return new ApiRestResult<>(resolvedStatus, resolvedMessage, result);
        }
    }
}
