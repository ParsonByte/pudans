package cn.tolim.pudans.iam.authorization.endpoint;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import cn.tolim.pudans.iam.infra.exception.enums.ExceptionStatus;
import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;


@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "cn.tolim.pudans.iam.authorization.endpoint")
public class AuthorizationExceptionHandler {

    // Handles authorization domain errors
    // Parameter exception: PudansException
    // Returns: ResponseEntity<ApiRestResult<Void>>
    @ExceptionHandler(PudansException.class)
    public ResponseEntity<ApiRestResult<Void>> handlePudansException(PudansException exception) {
        HttpStatus status = exception.getHttpStatus() == null ? HttpStatus.INTERNAL_SERVER_ERROR
                : exception.getHttpStatus();
        return ResponseEntity.status(status).body(ApiRestResult.err(exception.getMessage(),
                exception.getErrorCode()));
    }

    // Handles request body validation errors
    // Parameter exception: MethodArgumentNotValidException
    // Returns: ResponseEntity<ApiRestResult<Void>>
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiRestResult<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiRestResult.err(message,
                ExceptionStatus.EX000101.getCode()));
    }

    // Handles method parameter validation errors
    // Parameter exception: ConstraintViolationException
    // Returns: ResponseEntity<ApiRestResult<Void>>
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiRestResult<Void>> handleConstraintViolation(
            ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(ApiRestResult.err(exception.getMessage(),
                ExceptionStatus.EX000101.getCode()));
    }

    // Handles unreadable request bodies
    // Parameter exception: HttpMessageNotReadableException
    // Returns: ResponseEntity<ApiRestResult<Void>>
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiRestResult<Void>> handleUnreadableBody(
            HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiRestResult.err(
                "Unable to read request body: " + exception.getMostSpecificCause().getMessage(),
                ExceptionStatus.EX000100.getCode()));
    }

    // Handles invalid arguments raised by authorization services
    // Parameter exception: IllegalArgumentException
    // Returns: ResponseEntity<ApiRestResult<Void>>
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiRestResult<Void>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiRestResult.err(exception.getMessage(),
                ExceptionStatus.EX000100.getCode()));
    }
}