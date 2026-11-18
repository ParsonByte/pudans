package cn.tolim.pudans.core.user.endpoint;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ServerWebInputException;

class UserEndpointLocalCreateValidationTest {

    @Test
    void acceptsAValidLocalUser() {
        assertThatCode(() -> UserEndpoint.validateLocalCreate(new UserEndpoint.LocalCreateUserRequest(
                        "alice", "Alice", "alice@example.com", "secret123", null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsAUsernameWithoutDisplayNameOrEmail() {
        assertThatCode(() -> UserEndpoint.validateLocalCreate(new UserEndpoint.LocalCreateUserRequest(
                        "alice", null, null, "secret123", null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnOversizedDisplayName() {
        assertThatThrownBy(() -> UserEndpoint.validateLocalCreate(new UserEndpoint.LocalCreateUserRequest(
                        "alice", "a".repeat(81), null, "secret123", null, null)))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void rejectsInvalidUsernamePasswordAndEmail() {
        assertThatThrownBy(() -> UserEndpoint.validateLocalCreate(new UserEndpoint.LocalCreateUserRequest(
                        "Bad Name", "Alice", "alice@example.com", "secret123", null, null)))
                .isInstanceOf(ServerWebInputException.class);

        assertThatThrownBy(() -> UserEndpoint.validateLocalCreate(new UserEndpoint.LocalCreateUserRequest(
                        "alice", "Alice", "not-an-email", "secret123", null, null)))
                .isInstanceOf(ServerWebInputException.class);

        assertThatThrownBy(() -> UserEndpoint.validateLocalCreate(new UserEndpoint.LocalCreateUserRequest(
                        "alice", "Alice", "alice@example.com", "short", null, null)))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void acceptsALocalBatchUpToTheLimit() {
        var users = Collections.nCopies(
                200,
                new UserEndpoint.LocalCreateUserRequest(
                        "alice", null, null, "secret123", null, null));

        assertThatCode(() -> UserEndpoint.validateLocalBatch(new UserEndpoint.LocalBatchCreateRequest(users)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnEmptyLocalBatch() {
        assertThatThrownBy(() -> UserEndpoint.validateLocalBatch(new UserEndpoint.LocalBatchCreateRequest(null)))
                .isInstanceOf(ServerWebInputException.class);
        assertThatThrownBy(() -> UserEndpoint.validateLocalBatch(
                        new UserEndpoint.LocalBatchCreateRequest(List.of())))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void rejectsAnOversizedLocalBatch() {
        var users = Collections.nCopies(
                201,
                new UserEndpoint.LocalCreateUserRequest(
                        "alice", null, null, "secret123", null, null));

        assertThatThrownBy(() -> UserEndpoint.validateLocalBatch(
                        new UserEndpoint.LocalBatchCreateRequest(users)))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void rejectsANullEntryInALocalBatch() {
        var users = Arrays.asList(
                new UserEndpoint.LocalCreateUserRequest("alice", null, null, "secret123", null, null),
                null);

        assertThatThrownBy(() -> UserEndpoint.validateLocalBatch(
                        new UserEndpoint.LocalBatchCreateRequest(users)))
                .isInstanceOf(ServerWebInputException.class);
    }
}
