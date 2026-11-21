package cn.tolim.pudans.infra.feishu;

import cn.tolim.pudans.infra.setting.SecurityParameters;
import com.lark.oapi.Client;
import com.lark.oapi.core.request.RequestOptions;
import com.lark.oapi.core.response.TenantAccessTokenResp;
import com.lark.oapi.core.utils.UnmarshalRespUtil;
import com.lark.oapi.service.auth.v3.model.InternalTenantAccessTokenReq;
import com.lark.oapi.service.auth.v3.model.InternalTenantAccessTokenReqBody;
import com.lark.oapi.service.auth.v3.model.InternalTenantAccessTokenResp;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenReq;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenReqBody;
import com.lark.oapi.service.contact.v3.model.GetUserReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class FeishuSdkClient {

    private final SecurityParameters securitySettings;

    private final AtomicReference<CachedClient> cachedClient = new AtomicReference<>();

    // 注入安全参数，按当前配置创建飞书客户端。
    public FeishuSdkClient(SecurityParameters securitySettings) {
        this.securitySettings = securitySettings;
    }

    // 使用当前飞书设置获取或创建缓存客户端。
    private Client client(SecurityParameters.FeishuSettings settings) {
        return client(settings.appId(), settings.appSecret());
    }

    // 凭据变化时重建客户端，避免复用失效连接。
    private Client client(String appId, String appSecret) {
        var current = cachedClient.get();
        if (current != null
                && Objects.equals(current.appId(), appId)
                && Objects.equals(current.appSecret(), appSecret)) {
            return current.client();
        }
        var client = Client.newBuilder(appId, appSecret)
                .requestTimeout(5, TimeUnit.SECONDS)
                .logReqAtDebug(false)
                .build();
        cachedClient.set(new CachedClient(appId, appSecret, client));
        return client;
    }

    // 调用飞书授权码接口换取用户访问令牌。
    public Mono<Token> exchangeCode(String code) {
        var client = client(securitySettings.feishu());
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

    // 使用用户访问令牌读取其飞书资料。
    public Mono<UserInfo> fetchUserInfo(String accessToken) {
        var client = client(securitySettings.feishu());
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
                    StringUtils.hasText(data.getEmail()) ? data.getEmail() : data.getEnterpriseEmail(),
                    data.getMobile(),
                    data.getAvatarUrl());
        });
    }

    // 用指定应用凭据获取租户访问令牌。
    public Mono<String> tenantAccessToken(String appId, String appSecret) {
        return tenantAccessToken(client(appId, appSecret), appId, appSecret);
    }

    // 调用内部接口换取租户令牌，供连通性测试使用。
    private Mono<String> tenantAccessToken(Client client, String appId, String appSecret) {
        return call(() -> {
            var request = InternalTenantAccessTokenReq.newBuilder()
                    .internalTenantAccessTokenReqBody(InternalTenantAccessTokenReqBody.newBuilder()
                            .appId(appId)
                            .appSecret(appSecret)
                            .build())
                    .build();
            var response = client.auth().v3().tenantAccessToken().internal(request);
            return requireTenantAccessToken(response);
        });
    }

    // 从原始响应解析租户令牌，缺失时抛出异常。
    static String requireTenantAccessToken(InternalTenantAccessTokenResp response) {
        var raw = response.getRawResponse();
        var body = raw == null ? null : UnmarshalRespUtil.unmarshalResp(raw, TenantAccessTokenResp.class);
        if (body == null || !StringUtils.hasText(body.getTenantAccessToken())) {
            throw new FeishuSdkException(failure("飞书租户访问令牌请求失败", response.getCode(), response.getMsg()));
        }
        return body.getTenantAccessToken();
    }

    // 按用户标识前缀选择对应类型查询通讯录。
    public Mono<UserInfo> fetchContactUser(String userId) {
        var client = client(securitySettings.feishu());
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
                    StringUtils.hasText(user.getEmail()) ? user.getEmail() : user.getEnterpriseEmail(),
                    user.getMobile(),
                    avatar);
        });
    }

    // 以指定消息类型向接收方发送消息。
    public Mono<Void> sendMessage(String receiveId, String receiveIdType, String msgType, String content) {
        var client = client(securitySettings.feishu());
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

    // 在弹性线程池执行阻塞的客户端调用。
    private static <T> Mono<T> call(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }

    // 拼接错误前缀与飞书返回码和消息。
    private static String failure(String prefix, int code, String message) {
        return prefix + ": " + code + " " + message;
    }

    private record CachedClient(String appId, String appSecret, Client client) {}

    public record Token(String accessToken, long expiresIn) {}

    public record UserInfo(
            String openId, String unionId, String name, String email, String phone, String avatar) {}

    public static class FeishuSdkException extends RuntimeException {
        // 使用飞书返回的错误描述构造异常。
        public FeishuSdkException(String message) {
            super(message);
        }
    }
}
