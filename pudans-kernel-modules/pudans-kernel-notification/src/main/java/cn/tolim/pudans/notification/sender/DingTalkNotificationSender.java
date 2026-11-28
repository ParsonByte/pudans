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

// Sends messages through DingTalk robot webhooks or the DingTalk work notification API.
@Component
public class DingTalkNotificationSender implements NotificationSender {

    private static final String DEFAULT_BASE_URL = "https://oapi.dingtalk.com";

    private final RestClient restClient;

    public DingTalkNotificationSender() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.DINGTALK;
    }

    @Override
    public Set<ProviderType> providerTypes() {
        return Set.of(ProviderType.DINGTALK);
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
            return SendResult.failure("DingTalk notification request failed ("
                    + exception.getClass().getSimpleName() + ")");
        }
    }

    private SendResult sendByWebhook(NotificationMessage message, String webhookUrl) {
        JSONObject payload = new JSONObject();
        payload.put("msgtype", "text");
        JSONObject text = new JSONObject();
        text.put("content", composedText(message));
        payload.put("text", text);
        String body = restClient.post().uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload.toJSONString())
                .retrieve()
                .body(String.class);
        JSONObject document = JSON.parseObject(body);
        if (document.getIntValue("errcode") == 0) {
            return SendResult.success(null, body);
        }
        return SendResult.failure(document.getString("errmsg"), body);
    }

    private SendResult sendByApp(NotificationMessage message, NotificationProviderConfig config) {
        String appId = config.require(ProviderConfigKeys.APP_ID);
        String appSecret = config.require(ProviderConfigKeys.APP_SECRET);
        long agentId = Long.parseLong(config.require(ProviderConfigKeys.AGENT_ID));
        if (message.getRecipient() == null || message.getRecipient().isBlank()) {
            throw new NotificationValidationException("DingTalk user id is required");
        }
        String baseUrl = config.get(ProviderConfigKeys.BASE_URL);
        String resolvedBaseUrl = baseUrl == null ? DEFAULT_BASE_URL : baseUrl;
        String tokenUrl = resolvedBaseUrl + "/gettoken?appkey="
                + URLEncoder.encode(appId, StandardCharsets.UTF_8) + "&appsecret="
                + URLEncoder.encode(appSecret, StandardCharsets.UTF_8);
        String tokenBody = restClient.get().uri(tokenUrl).retrieve().body(String.class);
        JSONObject tokenDocument = JSON.parseObject(tokenBody);
        if (tokenDocument.getIntValue("errcode") != 0) {
            return SendResult.failure(tokenDocument.getString("errmsg"), tokenBody);
        }
        String accessToken = tokenDocument.getString("access_token");
        JSONObject payload = new JSONObject();
        payload.put("agent_id", agentId);
        payload.put("userid_list", message.getRecipient());
        JSONObject msg = new JSONObject();
        msg.put("msgtype", "text");
        JSONObject text = new JSONObject();
        text.put("content", composedText(message));
        msg.put("text", text);
        payload.put("msg", msg);
        String sendUrl = resolvedBaseUrl + "/topapi/message/corpconversation/asyncsend_v2"
                + "?access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        String body = restClient.post().uri(sendUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload.toJSONString())
                .retrieve()
                .body(String.class);
        JSONObject document = JSON.parseObject(body);
        if (document.getIntValue("errcode") == 0) {
            return SendResult.success(document.getString("task_id"), body);
        }
        return SendResult.failure(document.getString("errmsg"), body);
    }

    private String composedText(NotificationMessage message) {
        if (message.getSubject() == null || message.getSubject().isBlank()) {
            return message.getContent() == null ? "" : message.getContent();
        }
        return message.getSubject() + "\n" + (message.getContent() == null ? "" : message.getContent());
    }
}