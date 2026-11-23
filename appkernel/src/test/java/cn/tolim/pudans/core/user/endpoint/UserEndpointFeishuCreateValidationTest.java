package cn.tolim.pudans.core.user.endpoint;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ServerWebInputException;

class UserEndpointFeishuCreateValidationTest {

    @Test
    void acceptsFeishuUsersWithUnionOrOpenIds() {
        var users = List.of(
                new UserEndpoint.FeishuUserItem("alice", null, null, "ou_1", null, null, "normal-users"),
                new UserEndpoint.FeishuUserItem(null, null, null, null, "on_2", null, null));

        assertThatCode(() -> UserEndpoint.validateBatchCreate(new UserEndpoint.BatchCreateRequest(users)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAFeishuUserWithoutAProviderId() {
        assertThatThrownBy(() -> UserEndpoint.validateFeishuCreate(null, "alice", null))
                .isInstanceOf(ServerWebInputException.class)
                .hasMessageContaining("union_id");
    }

    @Test
    void rejectsInvalidFeishuUsernamesAndGroups() {
        assertThatThrownBy(() -> UserEndpoint.validateFeishuCreate("on_1", "Bad Name", null))
                .isInstanceOf(ServerWebInputException.class);
        assertThatThrownBy(() -> UserEndpoint.validateFeishuCreate("on_1", "alice", "a".repeat(64)))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void rejectsEmptyOversizedOrNullFeishuBatches() {
        assertThatThrownBy(() -> UserEndpoint.validateBatchCreate(null))
                .isInstanceOf(ServerWebInputException.class);
        assertThatThrownBy(
                        () -> UserEndpoint.validateBatchCreate(new UserEndpoint.BatchCreateRequest(List.of())))
                .isInstanceOf(ServerWebInputException.class);

        var oversized = Collections.nCopies(
                201,
                new UserEndpoint.FeishuUserItem("alice", null, null, "ou_1", null, null, null));
        assertThatThrownBy(
                        () -> UserEndpoint.validateBatchCreate(new UserEndpoint.BatchCreateRequest(oversized)))
                .isInstanceOf(ServerWebInputException.class);

        var withNull = Arrays.asList(
                new UserEndpoint.FeishuUserItem("alice", null, null, "ou_1", null, null, null), null);
        assertThatThrownBy(
                        () -> UserEndpoint.validateBatchCreate(new UserEndpoint.BatchCreateRequest(withNull)))
                .isInstanceOf(ServerWebInputException.class);
    }
}
