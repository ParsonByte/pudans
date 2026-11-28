package cn.tolim.pudans.iam.identitysource.scim;

import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.scim.client.TestScimServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ScimConfigValidatorTests {

    private TestScimServer server;
    private ScimConfig config;

    @BeforeEach
    void setUp() throws IOException {
        server = TestScimServer.start();
        config = new ScimConfig();
        config.setBaseUrl(server.baseUrl());
        config.setBearerToken("test-token");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void shouldRejectMissingBaseUrl() {
        ScimConfig invalid = new ScimConfig();
        invalid.setBearerToken("test-token");

        assertThrows(ApiCallException.class, () -> new ScimConfigValidator().validate(invalid));
    }

    @Test
    void shouldValidateReachableServer() {
        server.on("/scim/v2/ServiceProviderConfig",
                "{\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig\"]}");
        server.on("/scim/v2/Users",
                "{\"totalResults\":0,\"startIndex\":1,\"itemsPerPage\":0,\"Resources\":[]}");

        assertTrue(new ScimConfigValidator().validate(config));
    }

    @Test
    void shouldValidateWhenServiceProviderConfigIsUnavailable() {
        server.on("/scim/v2/Users",
                "{\"totalResults\":0,\"startIndex\":1,\"itemsPerPage\":0,\"Resources\":[]}");

        assertTrue(new ScimConfigValidator().validate(config));
    }

    @Test
    void shouldReportUnauthorizedServer() {
        TestScimServer.StubResponse unauthorized = new TestScimServer.StubResponse(401, """
                {"schemas":["urn:ietf:params:scim:api:messages:2.0:Error"],
                 "status":"401","detail":"invalid token"}
                """);
        server.on("/scim/v2/ServiceProviderConfig", unauthorized);
        server.on("/scim/v2/Users", unauthorized);

        assertThrows(ApiCallException.class, () -> new ScimConfigValidator().validate(config));
    }
}