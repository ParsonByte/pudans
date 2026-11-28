package cn.tolim.pudans.communal.core.contract;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ApiResponseTests {

    @Test
    void shouldCreateSuccessResponseWithData() {
        Map<String, String> data = Map.of("key", "value");

        ApiResponse<Map<String, String>> response = ApiResponse.ok(data);

        assertEquals(ApiResponse.SUCCESS_CODE, response.code());
        assertEquals(ApiResponse.SUCCESS_MESSAGE, response.message());
        assertSame(data, response.data());
    }

    @Test
    void shouldCreateSuccessResponseWithoutData() {
        ApiResponse<Void> response = ApiResponse.ok();

        assertEquals("OK", response.code());
        assertEquals("OK", response.message());
        assertNull(response.data());
    }

    @Test
    void shouldCreateErrorResponse() {
        ApiResponse<Void> response = ApiResponse.error("FORBIDDEN", "禁止访问");

        assertEquals("FORBIDDEN", response.code());
        assertEquals("禁止访问", response.message());
        assertNull(response.data());
    }
}