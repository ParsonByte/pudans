package cn.tolim.pudans.iam.authentication.webauthn.model;

import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnVerificationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Parsed clientDataJSON produced by the browser
public record CollectedClientData(String type, String challenge, String origin,
                                  boolean crossOrigin) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Parses the JSON document returned in clientDataJSON
    public static CollectedClientData parse(String json) {
        if (json == null || json.isBlank()) {
            throw new WebAuthnVerificationException("Client data JSON is empty");
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            return new CollectedClientData(
                    text(node, "type"),
                    text(node, "challenge"),
                    text(node, "origin"),
                    node.path("crossOrigin").asBoolean(false));
        } catch (Exception exception) {
            throw new WebAuthnVerificationException("Client data JSON is malformed", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new WebAuthnVerificationException(
                    "Client data field is missing: " + field);
        }
        return value.asText();
    }
}