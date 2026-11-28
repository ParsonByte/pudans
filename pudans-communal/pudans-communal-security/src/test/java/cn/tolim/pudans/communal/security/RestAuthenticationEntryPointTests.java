package cn.tolim.pudans.communal.security;

import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestAuthenticationEntryPointTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldWriteUnauthorizedJsonResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);

        entryPoint.commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("unauthorized"));

        assertEquals(401, response.getStatus());
        assertNotNull(response.getContentType());
        assertTrue(response.getContentType().startsWith("application/json"));
        String body = response.getContentAsString();
        assertTrue(body.contains(CommonErrorCode.UNAUTHORIZED.code()));
        assertTrue(body.contains(CommonErrorCode.UNAUTHORIZED.message()));
        assertTrue(body.contains("\"data\":null"));
    }

    @Test
    void shouldWriteUtf8ResponseThroughWriter() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        UnauthorizedResponseWriter.write(response, objectMapper);

        assertEquals(401, response.getStatus());
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
    }
}