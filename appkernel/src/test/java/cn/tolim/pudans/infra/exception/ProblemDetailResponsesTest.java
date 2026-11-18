package cn.tolim.pudans.infra.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import reactor.test.StepVerifier;

class ProblemDetailResponsesTest {

    private static final ServerResponse.Context CONTEXT = new ServerResponse.Context() {
        @Override
        public List<HttpMessageWriter<?>> messageWriters() {
            return ServerCodecConfigurer.create().getWriters();
        }

        @Override
        public List<ViewResolver> viewResolvers() {
            return List.of();
        }
    };

    @Test
    void pageNavigationRedirectsWithCodeAndDetail() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/oauth2/callback/feishu")
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                .build());

        StepVerifier.create(ProblemDetailResponses.writeOrRedirect(
                        exchange,
                        CONTEXT,
                        HttpStatus.UNAUTHORIZED,
                        "feishu_not_bound",
                        "该飞书账号尚未绑定 Pudans 账号。",
                        ProblemDetailResponses.SIGN_IN_PATH))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
        var location = exchange.getResponse().getHeaders().getLocation();
        assertThat(location).isNotNull();
        assertThat(location.getPath()).isEqualTo("/sign-in");
        assertThat(location.getQuery()).contains("code=feishu_not_bound").contains("detail=");
    }

    @Test
    void apiRequestKeepsTheProblemDetailResponse() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/oauth2/callback/feishu")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build());

        StepVerifier.create(ProblemDetailResponses.writeOrRedirect(
                        exchange,
                        CONTEXT,
                        HttpStatus.UNAUTHORIZED,
                        "feishu_not_bound",
                        "该飞书账号尚未绑定 Pudans 账号。",
                        ProblemDetailResponses.SIGN_IN_PATH))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("feishu_not_bound")
                .contains("该飞书账号尚未绑定 Pudans 账号。");
    }
}
