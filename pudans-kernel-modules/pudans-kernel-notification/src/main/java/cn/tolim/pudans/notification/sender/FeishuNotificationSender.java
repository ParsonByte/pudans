package cn.tolim.pudans.notification.sender;

import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import cn.tolim.pudans.notification.exception.NotificationValidationException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

// Sends messages through Feishu webhooks or the Feishu Open Platform message API.
@Component
public class FeishuNotificationSender implements NotificationSender {

    private static final String DEFAULT_BASE_URL = "https://open.feishu.cn/open-apis";
    private static final String DEFAULT_RECEIVE_ID_TYPE = "open_id";

    private final RestClient restClient;

    public FeishuNotificationSender() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.FEISHU;
    }

    @Override
    public Set<ProviderType> providerTypes() {
        return Set.of(ProviderType.FEISHU);
    }

    @Override
    public SendResult send(NotificationMessage message, NotificationProviderConfig config) {
        try {
            String webhookUrl = config.get(ProviderConfigKeys.WEBHOOK_URL);
            if (webhookUrl != null) {
                return sendByWebhook(message, webhookUrl);
            }
            return sendByApp(message, config);
        } catch (Exception exception) {
            return SendResult.failure("Feishu notification request failed ("
                    + exception.getClass().getSimpleName() + ")");
        }
    }

    private SendResult sendByWebhook(NotificationMessage message, String webhookUrl) {
        JSONObject payload = textPayload(message);
        String body = restClient.post().uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload.toJSONString())
                .retrieve()
                .body(String.class);
        JSONObject document = JSON.parseObject(body);
        int code = document.containsKey("code")
                ? document.getIntValue("code") : document.getIntValue("StatusCode");
        if (code == 0) {
            return SendResult.success(null, body);
        }
        return SendResult.failure(document.getString("msg"), body);
    }

    private SendResult sendByApp(NotificationMessage message, NotificationProviderConfig config) {
        String appId = config.require(ProviderConfigKeys.APP_ID);
        String appSecret = config.require(ProviderConfigKeys.APP_SECRET);
        String baseUrl = config.get(ProviderConfigKeys.BASE_URL);
        String resolvedBaseUrl = baseUrl == null ? DEFAULT_BASE_URL : baseUrl;
        JSONObject tokenRequest = new JSONObject();
        tokenRequest.put("app_id", appId);
        tokenRequest.put("app_secret", appSecret);
        String tokenBody = restClient.post()
                .uri(resolvedBaseUrl + "/auth/v3/tenant_access_token/internal")
                .contentType(MediaType.APPLICATION_JSON)
                .body(tokenRequest.toJSONString())
                .retrieve()
                .body(String.class);
        JSONObject tokenDocument = JSON.parseObject(tokenBody);
        if (tokenDocument.getIntValue("code") != 0) {
            return SendResult.failure(tokenDocument.getString("msg"), tokenBody);
        }
        String accessToken = tokenDocument.getString("tenant_access_token");
        if (accessToken == null || accessToken.isBlank()) {
            return SendResult.failure("Feishu did not return a tenant access token", tokenBody);
        }
        if (message.getRecipient() == null || message.getRecipient().isBlank()) {
            throw new NotificationValidationException("Feishu receive_id is required");
        }
        String receiveIdType = config.get(ProviderConfigKeys.RECEIVE_ID_TYPE);
        JSONObject payload = new JSONObject();
        payload.put("receive_id", message.getRecipient());
        payload.put("msg_type", "text");
        JSONObject content = new JSONObject();
        content.put("text", composedText(message));
        payload.put("content", content.toJSONString());
        String url = resolvedBaseUrl + "/im/v1/messages?receive_id_type="
                + URLEncoder.encode(receiveIdType == null ? DEFAULT_RECEIVE_ID_TYPE : receiveIdType,
                StandardCharsets.UTF_8);
        String body = restClient.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(payload.toJSONString())
                .retrieve()
                .body(String.class);
        JSONObject document = JSON.parseObject(body);
        if (document.getIntValue("code") == 0) {
            JSONObject data = document.getJSONObject("data");
            return SendResult.success(data == null ? null : data.getString("message_id"), body);
        }
        return SendResult.failure(document.getString("msg"), body);
    }

    private JSONObject textPayload(NotificationMessage message) {
        JSONObject payload = new JSONObject();
        payload.put("msg_type", "text");
        JSONObject content = new JSONObject();
        content.put("text", composedText(message));
        payload.put("content", content);
        return payload;
    }

    private String composedText(NotificationMessage message) {
        if (message.getSubject() == null || message.getSubject().isBlank()) {
            return message.getContent() == null ? "" : message.getContent();
        }
        return message.getSubject() + "\n" + (message.getContent() == null ? "" : message.getContent());
    }
}