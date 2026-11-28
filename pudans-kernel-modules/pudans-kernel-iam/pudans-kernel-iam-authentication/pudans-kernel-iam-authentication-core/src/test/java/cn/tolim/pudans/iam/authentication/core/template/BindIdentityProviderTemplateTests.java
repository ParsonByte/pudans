package cn.tolim.pudans.iam.authentication.core.template;

import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindIdentityProviderTemplateTests {

    private final BindIdentityProviderTemplate template = new BindIdentityProviderTemplate();

    @Test
    void shouldRenderBindingRedirectTemplate() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        template.sendAuthorizationResponse(response, ApiRestResult.<Void>ok());

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentType().startsWith("text/html"));
        assertEquals(StandardCharsets.UTF_8.name(), response.getCharacterEncoding());
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("Redirect - PUDANS"));
        assertTrue(body.contains("window.opener.postMessage"));
        assertTrue(body.contains("nonce="));
    }

    @Test
    void shouldReturnBadRequestWhenTemplateCannotBeWritten() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse() {
            @Override
            public PrintWriter getWriter() {
                throw new IllegalStateException("writer unavailable");
            }
        };

        template.sendAuthorizationResponse(response, ApiRestResult.ok());

        assertEquals(400, response.getStatus());
        assertEquals("writer unavailable", response.getErrorMessage());
    }
}