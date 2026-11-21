package cn.tolim.pudans.security.authentication.login;

import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.security.contract.authentication.LoginCipher;
import java.util.Base64;
import lombok.Data;
import org.springdoc.core.fn.builders.apiresponse.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

public class PublicKeyRouteBuilder {

    private final LoginCipher loginCipher;

    // 持有密文能力以暴露登录所需的公钥。
    public PublicKeyRouteBuilder(LoginCipher loginCipher) {
        this.loginCipher = loginCipher;
    }


    // 提供登录页加密密码所需的公钥接口。
    public RouterFunction<ServerResponse> build() {
        return SpringdocRouteBuilder.route()
                .GET(
                        ApiGroups.API_PREFIX + "/login/public-key",
                        request -> loginCipher.readPublicKey().flatMap(publicKey -> {
                            var base64Format = Base64.getEncoder().encodeToString(publicKey);
                            var response = new PublicKeyResponse();
                            response.setBase64Format(base64Format);
                            return ServerResponse.ok().bodyValue(response);
                        }),
                        builder -> builder.operationId("GetPublicKey")
                                .description("Read public key for encrypting password.")
                                .tag("LoginV1alpha1")
                                .response(Builder.responseBuilder().implementation(PublicKeyResponse.class)))
                .build();
    }

    @Data
    public static class PublicKeyResponse {

        private String base64Format;

        // 告知客户端优先使用 OAEP-SHA256 加密登录密码
        private String padding = "OAEP-SHA256";
    }
}
