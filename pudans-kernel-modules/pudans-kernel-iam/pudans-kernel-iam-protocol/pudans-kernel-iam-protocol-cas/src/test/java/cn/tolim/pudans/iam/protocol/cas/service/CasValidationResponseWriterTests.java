package cn.tolim.pudans.iam.protocol.cas.service;

import cn.tolim.pudans.iam.protocol.cas.exception.CasErrorCodes;
import cn.tolim.pudans.iam.protocol.cas.model.CasServiceValidationResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


class CasValidationResponseWriterTests {

    @Test
    void writesXmlSuccessWithAttributesForCas3() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CasValidationResponseWriter.writeValidationResponse(response,
                CasServiceValidationResult.success("alice",
                        Map.of("email", "alice@example.test")), true, false);

        assertThat(response.getContentType()).contains("application/xml");
        assertThat(response.getContentAsString()).contains("<cas:authenticationSuccess>");
        assertThat(response.getContentAsString()).contains("<cas:user>alice</cas:user>");
        assertThat(response.getContentAsString())
                .contains("<cas:email>alice@example.test</cas:email>");
    }

    @Test
    void omitsAttributesForCas2() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CasValidationResponseWriter.writeValidationResponse(response,
                CasServiceValidationResult.success("alice", Map.of("email", "a@test")),
                false, false);

        assertThat(response.getContentAsString()).doesNotContain("<cas:attributes>");
        assertThat(response.getContentAsString()).doesNotContain("<cas:email>");
    }

    @Test
    void writesXmlFailure() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CasValidationResponseWriter.writeValidationResponse(response,
                CasServiceValidationResult.failure(CasErrorCodes.INVALID_TICKET,
                        "Ticket is invalid"), false, false);

        assertThat(response.getContentAsString())
                .contains("<cas:authenticationFailure code=\"INVALID_TICKET\">"
                        + "Ticket is invalid</cas:authenticationFailure>");
    }

    @Test
    void writesJsonFailure() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CasValidationResponseWriter.writeValidationResponse(response,
                CasServiceValidationResult.failure(CasErrorCodes.INVALID_SERVICE,
                        "Service mismatch"), false, true);

        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("\"code\":\"INVALID_SERVICE\"");
        assertThat(response.getContentAsString()).contains("\"description\":\"Service mismatch\"");
    }

    @Test
    void writesTextValidationResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CasValidationResponseWriter.writeTextValidationResponse(response,
                CasServiceValidationResult.success("alice", Map.of()));

        assertThat(response.getContentAsString()).isEqualTo("yes\nalice\n");
    }

    @Test
    void writesTextFailureResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CasValidationResponseWriter.writeTextValidationResponse(response,
                CasServiceValidationResult.failure(CasErrorCodes.INVALID_TICKET, "bad"));

        assertThat(response.getContentAsString()).isEqualTo("no\n\n");
    }
}