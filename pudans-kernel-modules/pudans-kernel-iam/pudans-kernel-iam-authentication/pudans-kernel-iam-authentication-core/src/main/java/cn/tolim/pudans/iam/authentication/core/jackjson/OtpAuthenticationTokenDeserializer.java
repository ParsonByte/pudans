package cn.tolim.pudans.iam.authentication.core.jackjson;

import cn.tolim.pudans.iam.authentication.core.authentication.OtpAuthentication;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.springframework.security.core.GrantedAuthority;

import java.io.IOException;
import java.util.List;


@SuppressWarnings("DuplicatedCode")
class OtpAuthenticationTokenDeserializer extends JsonDeserializer<OtpAuthentication> {

    private static final TypeReference<List<GrantedAuthority>> GRANTED_AUTHORITY_LIST = new TypeReference<>() {
    };

    private static final TypeReference<Object> OBJECT = new TypeReference<>() {
    };

    @Override
    public OtpAuthentication deserialize(JsonParser jp,
                                         DeserializationContext deserializationContext) throws IOException {

        ObjectMapper mapper = (ObjectMapper) jp.getCodec();
        JsonNode jsonNode = mapper.readTree(jp);
        boolean authenticated = readJsonNode(jsonNode, "authenticated").asBoolean();
        String recipient = readJsonNode(jsonNode, "recipient").asText();
        String type = readJsonNode(jsonNode, "type").asText();
        JsonNode principalNode = readJsonNode(jsonNode, "principal");
        Object principal = getPrincipal(mapper, principalNode);
        // Permission
        List<GrantedAuthority> authorities = mapper.readValue(
                readJsonNode(jsonNode, "authorities").traverse(mapper), GRANTED_AUTHORITY_LIST);
        OtpAuthentication authentication = (!authenticated)
                ? new OtpAuthentication(principal, recipient, type)
                : new OtpAuthentication(principal, recipient, type, authorities);
        JsonNode detailsNode = readJsonNode(jsonNode, "details");
        if (detailsNode.isNull() || detailsNode.isMissingNode()) {
            authentication.setDetails(null);
        } else {
            Object details = mapper.readValue(detailsNode.toString(), OBJECT);
            authentication.setDetails(details);
        }
        return authentication;
    }

    private Object getPrincipal(ObjectMapper mapper, JsonNode principalNode) throws IOException {
        if (principalNode.isObject()) {
            return mapper.readValue(principalNode.traverse(mapper), Object.class);
        }
        return principalNode.asText();
    }

    private JsonNode readJsonNode(JsonNode jsonNode, String field) {
        return jsonNode.has(field) ? jsonNode.get(field) : MissingNode.getInstance();
    }

}
