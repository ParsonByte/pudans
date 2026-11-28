package cn.tolim.pudans.iam.identitysource.scim;

import cn.tolim.pudans.iam.identitysource.core.AbstractDefaultIdentitySource;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.event.IdentitySourceEventUtils;
import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceEventPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncDeptPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncUserPostProcessor;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimGroup;
import cn.tolim.pudans.iam.identitysource.scim.domain.ScimUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.EVENT_GROUP_NAME;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.EVENT_RESOURCE_ID;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.EVENT_RESOURCE_TYPE;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.EVENT_STATUS;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.EVENT_STATUS_ACCEPTED;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.EVENT_USER_NAME;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.GROUP_SCHEMA;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.LOGGER_NAME;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.RESOURCE_TYPE_GROUP;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.RESOURCE_TYPE_USER;
import static cn.tolim.pudans.iam.identitysource.scim.ScimConstants.USER_SCHEMA;


@Slf4j(topic = LOGGER_NAME)
public class ScimIdentitySource extends AbstractDefaultIdentitySource<ScimConfig> {

    private static final String EVENT_TIME = "eventTime";
    private static final String SCHEMAS = "schemas";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScimIdentitySource(String id, String name, ScimConfig config,
                              IdentitySourceClient identitySourceClient,
                              IdentitySourceSyncUserPostProcessor identitySourceSyncUserPostProcessor,
                              IdentitySourceSyncDeptPostProcessor identitySourceSyncDeptPostProcessor,
                              IdentitySourceEventPostProcessor identitySourceEventPostProcessor) {
        super(id, name, config, identitySourceClient, identitySourceSyncUserPostProcessor,
                identitySourceSyncDeptPostProcessor, identitySourceEventPostProcessor);
    }

    // Consume a SCIM resource notification and trigger a full reconciliation
    @Override
    public Object event(HttpServletRequest request, String body) {
        LocalDateTime eventTime = LocalDateTime.now();
        try {
            JsonNode payload = objectMapper.readTree(body);
            Map<String, Object> result = new LinkedHashMap<>();
            if (hasSchema(payload, USER_SCHEMA)) {
                ScimUser user = objectMapper.treeToValue(payload, ScimUser.class);
                if (StringUtils.isBlank(user.getUserName())) {
                    throw new ApiCallException("SCIM user event requires a userName");
                }
                result.put(EVENT_RESOURCE_TYPE, RESOURCE_TYPE_USER);
                result.put(EVENT_RESOURCE_ID, user.getId());
                result.put(EVENT_USER_NAME, user.getUserName());
                log.info("SCIM identity source [{}] received user event [{}]", getId(),
                        user.getUserName());
            } else if (hasSchema(payload, GROUP_SCHEMA)) {
                ScimGroup group = objectMapper.treeToValue(payload, ScimGroup.class);
                if (StringUtils.isBlank(group.getDisplayName())) {
                    throw new ApiCallException("SCIM group event requires a displayName");
                }
                result.put(EVENT_RESOURCE_TYPE, RESOURCE_TYPE_GROUP);
                result.put(EVENT_RESOURCE_ID, group.getId());
                result.put(EVENT_GROUP_NAME, group.getDisplayName());
                log.info("SCIM identity source [{}] received group event [{}]", getId(),
                        group.getDisplayName());
            } else {
                throw new ApiCallException("Unsupported SCIM event payload");
            }
            triggerReconciliation();
            result.put(EVENT_STATUS, EVENT_STATUS_ACCEPTED);
            result.put(EVENT_TIME, eventTime.toString());
            return result;
        } catch (ApiCallException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Invalid SCIM event payload for identity source [{}]: {}", getId(),
                    exception.getMessage());
            throw new ApiCallException("Invalid SCIM event payload: " + exception.getMessage(),
                    exception);
        }
    }

    // Publish an asynchronous full sync request through the identity source event channel
    private void triggerReconciliation() {
        try {
            IdentitySourceEventUtils.sync(getId());
        } catch (Exception exception) {
            log.warn("Unable to publish SCIM sync event for identity source [{}]: {}", getId(),
                    exception.getMessage());
        }
    }

    // Whether the payload declares the given SCIM schema
    private boolean hasSchema(JsonNode payload, String schema) {
        JsonNode schemas = payload.get(SCHEMAS);
        if (schemas == null || !schemas.isArray()) {
            return false;
        }
        for (JsonNode item : schemas) {
            if (schema.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }
}