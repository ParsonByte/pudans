package cn.tolim.pudans.communal.web;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.communal.core.error.ApiException;
import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<ApiResponse<Void>> badRequest(BindingResult bindingResult) {
        FieldError fieldError = bindingResult.getFieldError();
        String message = fieldError == null || fieldError.getDefaultMessage() == null || fieldError.getDefaultMessage().isBlank()
                ? CommonErrorCode.BAD_REQUEST.message()
                : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(CommonErrorCode.BAD_REQUEST.code(), message));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponse.error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException exception) {
        return ResponseEntity.status(CommonErrorCode.NOT_FOUND.status())
                .body(ApiResponse.error(CommonErrorCode.NOT_FOUND.code(),
                        CommonErrorCode.NOT_FOUND.message()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        return badRequest(exception.getBindingResult());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException exception) {
        return badRequest(exception.getBindingResult());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        LOGGER.error("未处理的异常", exception);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.error(CommonErrorCode.INTERNAL_ERROR.code(), CommonErrorCode.INTERNAL_ERROR.message()));
    }
}
