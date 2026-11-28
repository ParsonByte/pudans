package cn.tolim.pudans.iam.core.auth.session;

import cn.tolim.pudans.iam.communal.session.AppSessionEntity;
import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HttpBackchannelLogoutNotifier implements BackchannelLogoutNotifier {

    public static final String BACKCHANNEL_LOGOUT_EVENT = "http://schemas.openid.net/event/backchannel-logout";
    public static final String LOGOUT_TOKEN_TYPE = "LogoutToken";

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpBackchannelLogoutNotifier.class);

    private final IamAuthProperties properties;
    private final RestTemplate restTemplate;

    public HttpBackchannelLogoutNotifier(IamAuthProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getBackchannel().getConnectTimeout());
        requestFactory.setReadTimeout(properties.getBackchannel().getReadTimeout());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public int notifyLogout(String sid, String userId, String reason, List<AppSessionEntity> appSessions) {
        if (appSessions == null || appSessions.isEmpty()) {
            return 0;
        }
        int notified = 0;
        Map<String, String> logoutUrls = properties.getBackchannel().getAppUrls();
        for (AppSessionEntity appSession : appSessions) {
            String url = logoutUrls.get(appSession.getAppCode());
            if (url == null || url.isBlank()) {
                LOGGER.warn("No back-channel logout url configured for app [{}], session validation is the fallback",
                        appSession.getAppCode());
                continue;
            }
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                        buildPayload(sid, userId, appSession.getAppCode(), reason), headers);
                restTemplate.postForEntity(url, request, Void.class);
                notified++;
            } catch (RuntimeException exception) {
                LOGGER.warn("Back-channel logout to app [{}] at {} failed: {}", appSession.getAppCode(),
                        url, exception.getMessage());
            }
        }
        return notified;
    }

    // Builds an OpenID Connect style back-channel logout payload
    // Parameter sid: String
    // Parameter userId: String
    // Parameter appCode: String
    // Parameter reason: String
    // Returns: Map
    Map<String, Object> buildPayload(String sid, String userId, String appCode, String reason) {
        Map<String, Object> events = new LinkedHashMap<>();
        events.put(BACKCHANNEL_LOGOUT_EVENT, Map.of());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", "pudans-iam");
        payload.put("aud", appCode);
        payload.put("sid", sid);
        payload.put("sub", userId);
        payload.put("iat", Instant.now().getEpochSecond());
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("events", events);
        payload.put("reason", reason);
        return payload;
    }
}