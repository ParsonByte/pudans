package cn.tolim.pudans.job.contract;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.communal.core.error.ApiException;
import cn.tolim.pudans.job.support.JobConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

// Translates job API exceptions into the shared ApiResponse envelope
@RestControllerAdvice(basePackages = JobConstants.API_PACKAGE)
public class JobApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        int status = exception.getStatus() > 0 ? exception.getStatus() : 500;
        return ResponseEntity.status(status)
                .body(ApiResponse.error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(
            MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null
                ? "Request validation failed"
                : fieldError.getField() + ": " + fieldError.getDefaultMessage();
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("JOB_INVALID_REQUEST", message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParameterValidation(
            HandlerMethodValidationException exception) {
        String message = exception.getAllErrors().isEmpty()
                ? "Request parameter validation failed"
                : exception.getAllErrors().getFirst().getDefaultMessage();
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("JOB_INVALID_REQUEST", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(this::describe)
                .orElse("Request validation failed");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("JOB_INVALID_REQUEST", message));
    }

    private String describe(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + ": " + violation.getMessage();
    }
}