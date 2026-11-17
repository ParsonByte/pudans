package cn.tolim.pudans.core.user.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.mock.http.server.reactive.MockServerHttpRequest.get;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.extension.ListResult;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.router.SortableRequest;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.server.MockServerWebExchange;

class UserEndpointPaginationTest {

    private static final int TOTAL_USERS = 30;

    @ParameterizedTest
    @CsvSource({"1, 10", "2, 10", "3, 10", "2, 25"})
    void keywordPaginationUsesTheSameOneBasedPagesAsListResult(int page, int size) throws Exception {
        var users = users();

        var expected = metadataNames(ListResult.subList(users, page, size));
        var actual = listedNames(paged(users, page, size).items());

        assertThat(actual)
                .as("keyword pagination for page %s, size %s", page, size)
                .containsExactlyElementsOf(expected);
    }

    private static UserEndpoint.UserList paged(List<User> users, int page, int size) throws Exception {
        var endpoint = new UserEndpoint(null, null, null, null, null, null, null, null, null, null, null, null);
        Method method = UserEndpoint.class.getDeclaredMethod(
                "pagedUserList", List.class, Map.class, SortableRequest.class, boolean.class);
        method.setAccessible(true);
        return (UserEndpoint.UserList)
                method.invoke(endpoint, users, Map.of(), request(page, size), true);
    }

    private static SortableRequest request(int page, int size) {
        var exchange = MockServerWebExchange.from(
                get("/users?page=" + page + "&size=" + size).build());
        return new SortableRequest(exchange) {
            @Override
            public Sort getSort() {
                return Sort.unsorted();
            }
        };
    }

    private static List<User> users() {
        return IntStream.rangeClosed(1, TOTAL_USERS)
                .mapToObj(UserEndpointPaginationTest::user)
                .toList();
    }

    private static User user(int number) {
        var padded = String.format("%02d", number);
        var user = new User();
        var metadata = new Metadata();
        metadata.setName("user-" + padded);
        user.setMetadata(metadata);
        var spec = new User.UserSpec();
        spec.setDisplayName("User " + padded);
        spec.setRegisteredAt(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(number));
        user.setSpec(spec);
        return user;
    }

    private static List<String> metadataNames(List<User> users) {
        return users.stream().map(user -> user.getMetadata().getName()).toList();
    }

    private static List<String> listedNames(List<UserEndpoint.ListedUser> users) {
        return users.stream().map(UserEndpoint.ListedUser::name).toList();
    }
}
