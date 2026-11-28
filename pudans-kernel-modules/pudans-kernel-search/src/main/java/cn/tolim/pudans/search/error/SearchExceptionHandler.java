package cn.tolim.pudans.search.error;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SearchExceptionHandler {

    // Map search errors to the shared API envelope with the declared HTTP status
    @ExceptionHandler(SearchException.class)
    public ResponseEntity<ApiResponse<Void>> handleSearchException(SearchException exception) {
        SearchErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), exception.getMessage()));
    }
}