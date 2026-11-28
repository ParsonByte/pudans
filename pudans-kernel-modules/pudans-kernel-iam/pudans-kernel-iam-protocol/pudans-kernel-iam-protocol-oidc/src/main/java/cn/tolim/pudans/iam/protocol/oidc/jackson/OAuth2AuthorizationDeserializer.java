package cn.tolim.pudans.iam.protocol.oidc.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class OAuth2AuthorizationDeserializer extends JsonDeserializer<OAuth2Authorization> {

    private static final TypeReference<Set<String>> SET_TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final TypeReference<Map<String, Object>> ATTRIBUTES_REFERENCE = new TypeReference<>() {
    };

    private static final TypeReference<Map<String, OAuth2Authorization.Token<OAuth2Token>>> OAUTH2_TOKEN_REFERENCE = new TypeReference<>() {
    };

    private static final TypeReference<AuthorizationGrantType> GRANT_TYPE_TYPE_REFERENCE = new TypeReference<>() {
    };

    @Override
    public OAuth2Authorization deserialize(JsonParser jsonParser,
                                           DeserializationContext deserializationContext)
            throws IOException {
        ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
        JsonNode jsonNode = mapper.readTree(jsonParser);

        Set<String> authorizedScopes = mapper.convertValue(jsonNode.get("authorizedScopes"),
                SET_TYPE_REFERENCE);
        Map<String, Object> attributes = mapper.convertValue(jsonNode.get("attributes"),
                ATTRIBUTES_REFERENCE);
        Map<String, OAuth2Authorization.Token<OAuth2Token>> tokens = mapper.convertValue(
                jsonNode.get("tokens"), OAUTH2_TOKEN_REFERENCE);
        AuthorizationGrantType grantType = mapper
                .convertValue(jsonNode.get("authorizationGrantType"), GRANT_TYPE_TYPE_REFERENCE);

        String id = readJsonNode(jsonNode, "id").asText();
        String registeredClientId = readJsonNode(jsonNode, "registeredClientId").asText();
        String principalName = readJsonNode(jsonNode, "principalName").asText();
        OAuth2Authorization.Builder builder = new AuthorizationBuilder(registeredClientId)
                .id(id).principalName(principalName).authorizationGrantType(grantType)
                .authorizedScopes(authorizedScopes).attributes(map -> map.putAll(attributes));

        Optional.ofNullable(tokens.get(OAuth2AuthorizationCode.class.getName()))
                .ifPresent(token -> addToken(token, builder));
        Optional.ofNullable(tokens.get(OAuth2AccessToken.class.getName()))
                .ifPresent(token -> addToken(token, builder));
        Optional.ofNullable(tokens.get(OAuth2RefreshToken.class.getName()))
                .ifPresent(token -> addToken(token, builder));
        Optional.ofNullable(tokens.get(OidcIdToken.class.getName()))
                .ifPresent(token -> addToken(token, builder));

        return builder.build();
    }

    private void addToken(OAuth2Authorization.Token<OAuth2Token> token,
                          OAuth2Authorization.Builder builder) {
        builder.token(token.getToken(), metadata -> metadata.putAll(token.getMetadata()));
    }

    private JsonNode readJsonNode(JsonNode jsonNode, String field) {
        return jsonNode.has(field) ? jsonNode.get(field) : MissingNode.getInstance();
    }

    private static final class AuthorizationBuilder extends OAuth2Authorization.Builder {

        private AuthorizationBuilder(String registeredClientId) {
            super(registeredClientId);
        }
    }
}
