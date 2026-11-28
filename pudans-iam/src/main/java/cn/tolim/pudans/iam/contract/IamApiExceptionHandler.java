package cn.tolim.pudans.iam.contract;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class IamApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IamApiExceptionHandler.class);

    // Maps expected IAM exceptions to their declared HTTP status
    // Parameter exception: PudansException
    // Returns: ResponseEntity
    @ExceptionHandler(PudansException.class)
    public ResponseEntity<IamApiError> pudansException(PudansException exception) {
        HttpStatus status = exception.getHttpStatus() == null
                ? HttpStatus.INTERNAL_SERVER_ERROR : exception.getHttpStatus();
        LOGGER.debug("IAM request rejected: {} - {}", exception.getErrorCode(), exception.getMessage());
        return ResponseEntity.status(status)
                .body(new IamApiError(exception.getErrorCode(), exception.getMessage(), status.value()));
    }

    // Maps method security denials to 403
    // Parameter exception: AccessDeniedException
    // Returns: ResponseEntity
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<IamApiError> accessDenied(AccessDeniedException exception) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status)
                .body(new IamApiError("access_denied", "Access is denied", status.value()));
    }

    // Maps malformed request bodies to 400
    // Parameter exception: HttpMessageNotReadableException
    // Returns: ResponseEntity
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<IamApiError> unreadableBody(HttpMessageNotReadableException exception) {
        return badRequest("request_body_invalid", "Request body is missing or malformed");
    }

    // Maps missing request parameters to 400
    // Parameter exception: MissingServletRequestParameterException
    // Returns: ResponseEntity
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<IamApiError> missingParameter(MissingServletRequestParameterException exception) {
        return badRequest("request_parameter_missing", exception.getMessage());
    }

    // Maps validation failures to 400
    // Parameter exception: MethodArgumentNotValidException
    // Returns: ResponseEntity
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<IamApiError> validationFailure(Exception exception) {
        return badRequest("request_validation_failed", exception.getMessage());
    }

    // Maps unknown paths to 404 instead of the catch-all 500
    // Parameter exception: NoResourceFoundException or NoHandlerFoundException
    // Returns: ResponseEntity
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<IamApiError> notFound(Exception exception) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status)
                .body(new IamApiError("not_found", "Resource not found", status.value()));
    }

    // Catch all for unexpected failures
    // Parameter exception: Exception
    // Returns: ResponseEntity
    @ExceptionHandler(Exception.class)
    public ResponseEntity<IamApiError> unexpected(Exception exception) {
        LOGGER.error("Unexpected IAM endpoint failure", exception);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(new IamApiError("internal_server_error", "Internal server error", status.value()));
    }

    private ResponseEntity<IamApiError> badRequest(String error, String description) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new IamApiError(error, description, status.value()));
    }
}