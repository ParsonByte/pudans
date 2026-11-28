package cn.tolim.pudans.iam.identitysource.scim.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ScimDtoTests {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void shouldDeserializeUser() throws Exception {
        String json = """
                {
                  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
                  "id": "u1",
                  "externalId": "ext-1",
                  "userName": "alice@example.com",
                  "name": {"formatted": "Alice Chen", "givenName": "Alice", "familyName": "Chen"},
                  "displayName": "Alice",
                  "active": true,
                  "emails": [{"value": "alice@example.com", "type": "work", "primary": true}],
                  "phoneNumbers": [{"value": "+8613800000000", "type": "mobile"}],
                  "photos": [{"value": "https://example.com/alice.png", "type": "photo"}],
                  "groups": [{"value": "g1", "display": "admins"}],
                  "meta": {"resourceType": "User", "location": "/scim/v2/Users/u1"}
                }
                """;

        ScimUser user = objectMapper.readValue(json, ScimUser.class);

        assertEquals("u1", user.getId());
        assertEquals("alice@example.com", user.getUserName());
        assertEquals("Alice Chen", user.getName().getFormatted());
        assertTrue(user.getActive());
        assertEquals("alice@example.com", user.getEmails().getFirst().getValue());
        assertEquals("g1", user.getGroups().getFirst().getValue());
        assertEquals("User", user.getMeta().getResourceType());
    }

    @Test
    void shouldSerializeUserWithoutNullFields() throws Exception {
        ScimUser user = new ScimUser();
        user.setSchemas(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
        user.setUserName("bob@example.com");
        user.setActive(false);
        ScimMultiValue email = new ScimMultiValue();
        email.setValue("bob@example.com");
        user.setEmails(List.of(email));

        String json = objectMapper.writeValueAsString(user);

        assertTrue(json.contains("\"userName\":\"bob@example.com\""));
        assertTrue(json.contains("\"active\":false"));
        assertFalse(json.contains("displayName"));
    }

    @Test
    void shouldDeserializeGroupAndMembers() throws Exception {
        String json = """
                {
                  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:Group"],
                  "id": "g1",
                  "displayName": "admins",
                  "members": [{"value": "u1", "display": "Alice", "type": "User"}]
                }
                """;

        ScimGroup group = objectMapper.readValue(json, ScimGroup.class);

        assertEquals("g1", group.getId());
        assertEquals("admins", group.getDisplayName());
        assertEquals("u1", group.getMembers().getFirst().getValue());
        assertEquals("User", group.getMembers().getFirst().getType());
    }

    @Test
    void shouldDeserializeListResponse() throws Exception {
        String json = """
                {
                  "schemas": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
                  "totalResults": 1,
                  "startIndex": 1,
                  "itemsPerPage": 1,
                  "Resources": [{"id": "u1", "userName": "alice"}]
                }
                """;

        ScimUserListResponse response = objectMapper.readValue(json, ScimUserListResponse.class);

        assertEquals(1, response.getTotalResults());
        assertEquals(1, response.getStartIndex());
        assertEquals("u1", response.getResources().getFirst().getId());
    }

    @Test
    void shouldSerializePatchOperation() throws Exception {
        ScimPatchOp patchOp = new ScimPatchOp();
        patchOp.setOperations(List.of(new ScimPatchOperation("replace", "active", false)));

        String json = objectMapper.writeValueAsString(patchOp);

        assertTrue(json.contains("\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:PatchOp\"]"));
        assertTrue(json.contains("\"Operations\":[{\"op\":\"replace\",\"path\":\"active\",\"value\":false}]"));
    }
}