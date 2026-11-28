package cn.tolim.pudans.communal.feign;

import cn.tolim.pudans.communal.core.error.ApiException;
import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PudansErrorDecoderTests {

    private final PudansErrorDecoder decoder = new PudansErrorDecoder(JsonMapper.builder().build(), false);

    @Test
    void mapsApiResponseCodeAndMessage() {
        Exception exception = decoder.decode("OrderClient#read", response(403,
                "{\"code\":\"FORBIDDEN\",\"message\":\"no access\"}"));

        assertThat(exception).isInstanceOf(ApiException.class);
        ApiException apiException = (ApiException) exception;
        assertThat(apiException.getCode()).isEqualTo("FORBIDDEN");
        assertThat(apiException.getStatus()).isEqualTo(403);
        assertThat(apiException.getMessage()).contains("OrderClient#read").contains("no access");
    }

    @Test
    void fallsBackToStatusCodesForNonJsonBodies() {
        Exception exception = decoder.decode("OrderClient#read", response(500, "<html>boom</html>"));

        ApiException apiException = (ApiException) exception;
        assertThat(apiException.getCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR.code());
        assertThat(apiException.getStatus()).isEqualTo(500);
    }

    @Test
    void mapsRateLimitAndConflictStatuses() {
        assertThat(((ApiException) decoder.decode("c#a", response(429, ""))).getCode())
                .isEqualTo("TOO_MANY_REQUESTS");
        assertThat(((ApiException) decoder.decode("c#a", response(409, ""))).getCode())
                .isEqualTo("CONFLICT");
        assertThat(((ApiException) decoder.decode("c#a", response(418, ""))).getCode())
                .isEqualTo("HTTP_418");
    }

    @Test
    void doesNotIncludeResponseBodyByDefault() {
        Exception exception = decoder.decode("OrderClient#read", response(400,
                "{\"code\":\"BAD_REQUEST\",\"message\":\"invalid\",\"data\":\"secret-value\"}"));

        assertThat(exception.getMessage()).doesNotContain("secret-value");
    }

    @Test
    void includesResponseBodyOnlyWhenEnabled() {
        PudansErrorDecoder verboseDecoder = new PudansErrorDecoder(JsonMapper.builder().build(), true);

        Exception exception = verboseDecoder.decode("OrderClient#read", response(400,
                "{\"code\":\"BAD_REQUEST\",\"message\":\"invalid\",\"data\":\"visible-value\"}"));

        assertThat(exception.getMessage()).contains("visible-value");
    }

    private Response response(int status, String body) {
        Request request = Request.create(Request.HttpMethod.GET, "http://localhost/api", Map.of(),
                null, StandardCharsets.UTF_8);
        return Response.builder()
                .status(status)
                .reason("")
                .request(request)
                .headers(Map.of("Content-Type", List.of("application/json")))
                .body(body, StandardCharsets.UTF_8)
                .build();
    }
}