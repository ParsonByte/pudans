package cn.tolim.pudans.infra.feishu;

import cn.tolim.pudans.infra.properties.FeishuProperties;
import com.lark.oapi.Client;
import com.lark.oapi.core.request.RequestOptions;
import com.lark.oapi.service.auth.v3.model.InternalTenantAccessTokenReq;
import com.lark.oapi.service.auth.v3.model.InternalTenantAccessTokenReqBody;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenReq;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenReqBody;
import com.lark.oapi.service.contact.v3.model.GetUserReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class FeishuSdkClient {

    private final FeishuProperties properties;

    private final Client client;

    public FeishuSdkClient(FeishuProperties properties) {
        this.properties = properties;
        this.client = Client.newBuilder(properties.getAppId(), properties.getAppSecret())
                .requestTimeout(5, TimeUnit.SECONDS)
                .logReqAtDebug(false)
                .build();
    }

    public Mono<Token> exchangeCode(String code) {
        return call(() -> {
            var request = CreateAccessTokenReq.newBuilder()
                    .createAccessTokenReqBody(CreateAccessTokenReqBody.newBuilder()
                            .grantType("authorization_code")
                            .code(code)
                            .build())
                    .build();
            var response = client.authen().v1().accessToken().create(request);
            if (!response.success()
                    || response.getData() == null
                    || !StringUtils.hasText(response.getData().getAccessToken())) {
                throw new FeishuSdkException(failure("飞书令牌交换失败", response.getCode(), response.getMsg()));
            }
            var data = response.getData();
            return new Token(
                    data.getAccessToken(),
                    data.getExpiresIn() == null ? 0L : data.getExpiresIn().longValue());
        });
    }

    public Mono<UserInfo> fetchUserInfo(String accessToken) {
        return call(() -> {
            var options = RequestOptions.newBuilder()
                    .userAccessToken(accessToken)
                    .build();
            var response = client.authen().v1().userInfo().get(options);
            if (!response.success() || response.getData() == null) {
                throw new FeishuSdkException(failure("飞书用户信息请求失败", response.getCode(), response.getMsg()));
            }
            var data = response.getData();
            return new UserInfo(
                    data.getOpenId(),
                    data.getUnionId(),
                    data.getName(),
                    data.getEmail(),
                    data.getAvatarUrl());
        });
    }

    public Mono<String> tenantAccessToken() {
        return call(() -> {
            var request = InternalTenantAccessTokenReq.newBuilder()
                    .internalTenantAccessTokenReqBody(InternalTenantAccessTokenReqBody.newBuilder()
                            .appId(properties.getAppId())
                            .appSecret(properties.getAppSecret())
                            .build())
                    .build();
            var response = client.auth().v3().tenantAccessToken().internal(request);
            if (!response.success()
                    || response.getData() == null
                    || !StringUtils.hasText(response.getData().getTenantAccessToken())) {
                throw new FeishuSdkException(failure("飞书租户访问令牌请求失败", response.getCode(), response.getMsg()));
            }
            return response.getData().getTenantAccessToken();
        });
    }

    public Mono<UserInfo> fetchContactUser(String userId) {
        return call(() -> {
            var userIdType = userId != null && userId.startsWith("on_") ? "union_id" : "open_id";
            var request = GetUserReq.newBuilder()
                    .userId(userId)
                    .userIdType(userIdType)
                    .build();
            var response = client.contact().v3().user().get(request);
            if (!response.success()
                    || response.getData() == null
                    || response.getData().getUser() == null) {
                throw new FeishuSdkException(failure("飞书通讯录用户请求失败", response.getCode(), response.getMsg()));
            }
            var user = response.getData().getUser();
            var avatar = user.getAvatar() == null ? null : user.getAvatar().getAvatar640();
            return new UserInfo(
                    user.getOpenId(),
                    user.getUnionId(),
                    user.getName(),
                    user.getEmail(),
                    avatar);
        });
    }

    public Mono<Void> sendMessage(String receiveId, String receiveIdType, String msgType, String content) {
        return call(() -> {
            var request = CreateMessageReq.newBuilder()
                    .receiveIdType(receiveIdType)
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                            .receiveId(receiveId)
                            .msgType(msgType)
                            .content(content)
                            .build())
                    .build();
            var response = client.im().v1().message().create(request);
            if (!response.success()) {
                throw new FeishuSdkException(failure("飞书消息请求失败", response.getCode(), response.getMsg()));
            }
            return response;
        }).then();
    }

    private static <T> Mono<T> call(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }

    private static String failure(String prefix, int code, String message) {
        return prefix + ": " + code + " " + message;
    }

    public record Token(String accessToken, long expiresIn) {}

    public record UserInfo(String openId, String unionId, String name, String email, String avatar) {}

    public static class FeishuSdkException extends RuntimeException {
        public FeishuSdkException(String message) {
            super(message);
        }
    }
}
