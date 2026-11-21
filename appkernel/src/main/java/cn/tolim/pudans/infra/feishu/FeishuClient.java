package cn.tolim.pudans.infra.feishu;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class FeishuClient {

    private final FeishuSdkClient sdkClient;

    // 用授权码换取访问令牌，并统一异常类型。
    public Mono<FeishuToken> exchangeCode(String code) {
        return sdkClient
                .exchangeCode(code)
                .map(token -> new FeishuToken(token.accessToken(), token.expiresIn()))
                .onErrorMap(this::mapError);
    }

    // 用访问令牌获取飞书用户信息。
    public Mono<FeishuUserInfo> fetchUserInfo(String accessToken) {
        return sdkClient
                .fetchUserInfo(accessToken)
                .map(this::toUserInfo)
                .onErrorMap(this::mapError);
    }

    // 验证应用凭据能否换取租户令牌。
    public Mono<String> testConnection(String appId, String appSecret) {
        return sdkClient.tenantAccessToken(appId, appSecret).onErrorMap(this::mapError);
    }

    // 按用户标识读取通讯录用户信息。
    public Mono<FeishuUserInfo> fetchContactUser(String userId) {
        return sdkClient
                .fetchContactUser(userId)
                .map(this::toUserInfo)
                .onErrorMap(this::mapError);
    }

    // 向指定群聊发送交互式告警卡片。
    public Mono<Void> sendAlertCard(String chatId, String contentJson) {
        return sdkClient
                .sendMessage(chatId, "chat_id", "interactive", contentJson)
                .onErrorMap(this::mapError);
    }

    // 将飞书客户端用户模型转换为领域无关信息。
    private FeishuUserInfo toUserInfo(FeishuSdkClient.UserInfo userInfo) {
        return new FeishuUserInfo(
                userInfo.openId(),
                userInfo.unionId(),
                userInfo.name(),
                userInfo.email(),
                userInfo.phone(),
                userInfo.avatar());
    }

    // 将飞书客户端异常包装为统一异常类型。
    private Throwable mapError(Throwable error) {
        if (error instanceof FeishuException) {
            return error;
        }
        var message = error.getMessage() == null ? "飞书 SDK 请求失败" : error.getMessage();
        return new FeishuException(message, error);
    }

    public record FeishuToken(String accessToken, long expiresIn) {}

    public record FeishuUserInfo(
            String openId, String unionId, String name, String email, String phone, String avatar) {}

    public static class FeishuException extends RuntimeException {
        // 保留原始客户端异常，便于定位调用失败原因。
        public FeishuException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
