package cn.tolim.pudans.iam.identitysource.dingtalk;

import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceProvider;
import cn.tolim.pudans.iam.identitysource.core.AbstractDefaultIdentitySource;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.enums.IdentitySourceEventReceiveType;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceEventPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncDeptPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncUserPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.modal.IdentitySourceEventProcessData;
import cn.tolim.pudans.iam.identitysource.dingtalk.enums.DingTalkEventType;
import cn.tolim.pudans.iam.identitysource.dingtalk.util.DingTalkEventCryptoUtils;
import cn.tolim.pudans.iam.infra.util.HttpRequestUtils;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;


@Slf4j
public class DingTalkIdentitySource extends AbstractDefaultIdentitySource<DingTalkConfig> {

    public static final String ENCRYPT = "encrypt";
    public static final String MSG_SIGNATURE = "msg_signature";
    public static final String TIMESTAMP = "timestamp";
    public static final String NONCE = "nonce";
    public static final String SUCCESS = "success";

    public DingTalkIdentitySource(String id, String name, DingTalkConfig config,
                                  IdentitySourceClient identitySourceClient,
                                  IdentitySourceSyncUserPostProcessor identitySourceSyncUserPostProcessor,
                                  IdentitySourceSyncDeptPostProcessor identitySourceSyncDeptPostProcessor,
                                  IdentitySourceEventPostProcessor identitySourceEventPostProcessor) {
        super(id, name, config, identitySourceClient, identitySourceSyncUserPostProcessor,
                identitySourceSyncDeptPostProcessor, identitySourceEventPostProcessor);
    }

    // Callback
    // See [book events](https://open.dingtalk.com/document/orgapp-server/address-book-events)
    // Parameter request HttpServletRequest
    @Override
    public Object event(HttpServletRequest request, String body) {
        LocalDateTime eventTime = LocalDateTime.now();
        Map<String, String> params = HttpRequestUtils.getRequestParameters(request);
        if (StringUtils.isNoneBlank(body)) {
            String encrypt = JSON.parseObject(body).getString(ENCRYPT);
            log.info("钉钉身份源 [{}] 回调入参: {}, encrypt: {}", getId(), JSON.toJSONString(params),
                    encrypt);
            Object result = eventCallBack(eventTime, params, encrypt);
            log.debug("钉钉身份源 [{}] 回调返回: {}", getId(), JSON.toJSONString(result));
            return result;
        }
        return null;
    }

    // 1. Read encryption parameters from the HTTP request
    // 2. Use the encryption/decryption type
    // Constant.OWNER_KEY notes:
    // 1. For application-level subscription events configured in the developer console, OWNER_KEY is the application APP_KEY.
    // 2. For enterprise-level subscription events, OWNER_KEY is the enterprise appkey (internal application) or SUITE_KEY (third-party application).
    // Parameter syncMap Map
    // Returns Map
    private Object eventCallBack(LocalDateTime eventTime, Map<String, String> syncMap,
                                 String encrypt) {
        try {
            DingTalkConfig config = getConfig();
            String msgSignature = syncMap.get(MSG_SIGNATURE);
            String timeStamp = syncMap.get(TIMESTAMP);
            String nonce = syncMap.get(NONCE);
            DingTalkEventCryptoUtils eventCryptoUtils = new DingTalkEventCryptoUtils(
                    config.getToken(), config.getAesKey(), config.getAppKey());
            String decryptMsg = eventCryptoUtils.getDecryptMsg(msgSignature, timeStamp, nonce,
                    encrypt);
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            DingTalkEventRequest callbackRequest = objectMapper.readValue(decryptMsg,
                    DingTalkEventRequest.class);
            // 4. Dispatch by EventType
            IdentitySourceEventReceiveType eventType = null;
            List<?> param = new ArrayList<>();
            List<String> idList = callbackRequest.getIdList();
            log.info("处理钉钉身份源 [{}] [{}] 事件,参数: [{}]", this.getId(),
                    callbackRequest.getEventType().getName(), callbackRequest);
            switch (callbackRequest.getEventType()) {
                case CHECK_URL:
                    break;
                case USER_ADD_ORG:
                    eventType = IdentitySourceEventReceiveType.USER_ADD;
                    param = getData(idList, this.getIdentitySourceClient()::getUser);
                    break;
                case USER_MODIFY_ORG:
                case USER_ACTIVE_ORG:
                    eventType = IdentitySourceEventReceiveType.USER_MODIFY;
                    param = getData(idList, this.getIdentitySourceClient()::getUser);
                    break;
                case USER_LEAVE_ORG:
                    eventType = IdentitySourceEventReceiveType.USER_LEAVE;
                    param = idList;
                    break;
                case ORG_DEPT_CREATE:
                    eventType = IdentitySourceEventReceiveType.DEPT_CREATE;
                    param = getData(idList, this.getIdentitySourceClient()::getDept);
                    break;
                case ORG_DEPT_MODIFY:
                    eventType = IdentitySourceEventReceiveType.DEPT_MODIFY;
                    param = getData(idList, this.getIdentitySourceClient()::getDept);
                    break;
                case ORG_DEPT_REMOVE:
                    eventType = IdentitySourceEventReceiveType.DEPT_REMOVE;
                    param = idList;
                    break;
                default:
                    throw new IllegalArgumentException("钉钉身份提供商回调非法事件");
            }
            if (eventType != null) {
                IdentitySourceEventProcessData<?> data = new IdentitySourceEventProcessData<>(
                        getId(), param, IdentitySourceProvider.DINGTALK, eventTime, eventType);
                identitySourceEventPostProcessor.process(data);
            }
            // 5. Return success with encrypted data
            return eventCryptoUtils.getEncryptedMap(SUCCESS);
        } catch (Exception e) {
            log.error("钉钉身份源 [{}] 回调发生异常: {}", getId(), e.getMessage());
        }
        return Collections.emptyMap();
    }

    // Get DingTalk event data
    // Parameter idList List
    // Parameter function Function
    private <T> List<T> getData(List<String> idList, Function<String, T> function) {
        List<T> list = new ArrayList<>();
        idList.forEach(item -> list.add(function.apply(item)));
        return list;
    }

    @Data
    public static class DingTalkEventRequest implements Serializable {

        @Serial
        private static final long serialVersionUID = 856826238722398023L;

        @JsonAlias(value = "CorpId")
        private String corpId;
        @JsonAlias(value = "EventType")
        private DingTalkEventType eventType;
        @JsonAlias(value = "TimeStamp")
        private String timeStamp;
        @JsonAlias(value = {"UserId", "DeptId"})
        private List<String> idList;
        @JsonAlias(value = "OptStaffId")
        private String optStaffId;

        public DingTalkEventRequest() {
        }
    }
}
