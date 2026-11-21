package cn.tolim.pudans.core.infra.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;

class ExceptionsTest {

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
    }

    @Test
    void badCredentialsUseChineseDetailAndTitle() {
        var response = Exceptions.createErrorResponse(
                new BadCredentialsException("Bad credentials"), HttpStatus.UNAUTHORIZED, exchange());

        assertEquals(401, response.getStatusCode().value());
        assertEquals("无效凭据", response.getBody().getTitle());
        assertEquals("用户名或密码错误。", response.getBody().getDetail());
    }

    @Test
    void serverErrorUsesGenericChineseMessage() {
        var response = Exceptions.createErrorResponse(new IllegalStateException("boom"), null, exchange());

        assertEquals(500, response.getStatusCode().value());
        assertEquals("服务器内部错误", response.getBody().getTitle());
        assertEquals("服务器内部发生错误，请稍候再试。", response.getBody().getDetail());
    }

    @Test
    void validationExceptionUsesChineseDetailAndErrors() {
        var errors = new BeanPropertyBindingResult(new Object(), "form");
        errors.reject("required", "名称不能为空");

        var exception = new RequestBodyValidationException(errors);

        assertEquals("请求内容无效。", exception.getBody().getDetail());
        var properties = exception.getBody().getProperties();
        assertNotNull(properties);
        assertEquals(List.of("名称不能为空"), properties.get("errors"));
    }
}
