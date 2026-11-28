package cn.tolim.pudans.communal.core.error;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiExceptionTests {

    @Test
    void shouldMapEveryCommonErrorCode() {
        for (CommonErrorCode errorCode : CommonErrorCode.values()) {
            ApiException exception = new ApiException(errorCode);

            assertEquals(errorCode.code(), exception.getCode());
            assertEquals(errorCode.status(), exception.getStatus());
            assertEquals(errorCode.message(), exception.getMessage());
        }
    }

    @Test
    void shouldKeepExplicitCodeStatusAndMessage() {
        ApiException exception = new ApiException("CUSTOM", 422, "自定义错误");

        assertEquals("CUSTOM", exception.getCode());
        assertEquals(422, exception.getStatus());
        assertEquals("自定义错误", exception.getMessage());
    }

    @Test
    void shouldExposeErrorMessageAsDefaultMessage() {
        CommonErrorCode errorCode = CommonErrorCode.NOT_FOUND;

        ApiException exception = assertThrows(ApiException.class, () -> {
            throw new ApiException(errorCode);
        });

        assertEquals(errorCode.message(), exception.getMessage());
    }

    @Test
    void shouldExposeStableErrorCodesAndStatuses() {
        assertEquals("UNAUTHORIZED", CommonErrorCode.UNAUTHORIZED.code());
        assertEquals(401, CommonErrorCode.UNAUTHORIZED.status());
        assertEquals("未认证", CommonErrorCode.UNAUTHORIZED.message());
        assertEquals(500, CommonErrorCode.INTERNAL_ERROR.status());
    }
}