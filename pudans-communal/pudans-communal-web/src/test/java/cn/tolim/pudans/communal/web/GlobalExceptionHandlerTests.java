package cn.tolim.pudans.communal.web;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.communal.core.error.ApiException;
import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // Form object with the fields used by the binding errors
    public static class Form {
        private String username;
        private String name;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    void shouldMapApiExceptionToConfiguredStatus() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleApiException(new ApiException(CommonErrorCode.FORBIDDEN));

        assertEquals(403, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("FORBIDDEN", response.getBody().code());
        assertEquals("禁止访问", response.getBody().message());
    }

    @Test
    void shouldMapExplicitApiExceptionStatus() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleApiException(new ApiException("CUSTOM", 422, "参数错误"));

        assertEquals(422, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("CUSTOM", response.getBody().code());
        assertEquals("参数错误", response.getBody().message());
    }

    @Test
    void shouldMapBindExceptionFieldError() {
        BindException exception = new BindException(new Form(), "form");
        exception.rejectValue("username", "NotBlank", "用户名不能为空");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBindException(exception);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", response.getBody().code());
        assertEquals("用户名不能为空", response.getBody().message());
    }

    @Test
    void shouldFallBackToDefaultMessageWhenFieldMessageIsBlank() {
        BindException exception = new BindException(new Form(), "form");
        exception.addError(new ObjectError("form", " "));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBindException(exception);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("错误请求", response.getBody().message());
    }

    @Test
    void shouldMapMethodArgumentNotValidException() {
        BindException bindingResult = new BindException(new Form(), "form");
        bindingResult.rejectValue("name", "NotBlank", "名称不能为空");
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMethodArgumentNotValid(exception);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("名称不能为空", response.getBody().message());
    }

    @Test
    void shouldHideUnexpectedExceptionDetails() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleException(new IllegalStateException("database down"));

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
        assertEquals("服务器内部错误", response.getBody().message());
    }
}