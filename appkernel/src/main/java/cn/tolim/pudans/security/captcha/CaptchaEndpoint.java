package cn.tolim.pudans.security.captcha;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;

import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

@Component
@RequiredArgsConstructor
public class CaptchaEndpoint implements CustomEndpoint {

    private final Captchas captchas;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "CaptchaV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/captcha/challenge", request -> captchas
                        .newChallenge()
                        .flatMap(challenge -> ServerResponse.ok().bodyValue(challenge)), builder -> builder
                        .operationId("GetCaptchaChallenge")
                        .description("Create an image captcha challenge.")
                        .tag(tag)
                        .response(responseBuilder().implementation(Captchas.CaptchaChallenge.class)))
                .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(ApiGroups.API, ApiGroups.VERSION);
    }
}
