package cn.tolim.pudans.infra.feishu;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class FeishuClient {

    private final FeishuSdkClient sdkClient;

    public Mono<FeishuToken> exchangeCode(String code) {
        return sdkClient
                .exchangeCode(code)
                .map(token -> new FeishuToken(token.accessToken(), token.expiresIn()))
                .onErrorMap(this::mapError);
    }

    public Mono<FeishuUserInfo> fetchUserInfo(String accessToken) {
        return sdkClient
                .fetchUserInfo(accessToken)
                .map(this::toUserInfo)
                .onErrorMap(this::mapError);
    }

    public Mono<String> tenantAccessToken() {
        return sdkClient.tenantAccessToken().onErrorMap(this::mapError);
    }

    public Mono<FeishuUserInfo> fetchContactUser(String userId) {
        return sdkClient
                .fetchContactUser(userId)
                .map(this::toUserInfo)
                .onErrorMap(this::mapError);
    }

    public Mono<Void> sendAlertCard(String chatId, String contentJson) {
        return sdkClient
                .sendMessage(chatId, "chat_id", "interactive", contentJson)
                .onErrorMap(this::mapError);
    }

    private FeishuUserInfo toUserInfo(FeishuSdkClient.UserInfo userInfo) {
        return new FeishuUserInfo(
                userInfo.openId(), userInfo.unionId(), userInfo.name(), userInfo.email(), userInfo.avatar());
    }

    private Throwable mapError(Throwable error) {
        if (error instanceof FeishuException) {
            return error;
        }
        var message = error.getMessage() == null ? "飞书 SDK 请求失败" : error.getMessage();
        return new FeishuException(message, error);
    }

    public record FeishuToken(String accessToken, long expiresIn) {}

    public record FeishuUserInfo(String openId, String unionId, String name, String email, String avatar) {}

    public static class FeishuException extends RuntimeException {
        public FeishuException(String message) {
            super(message);
        }

        public FeishuException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
