package cn.tolim.pudans.core.user.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.index.query.EmptyCondition;
import cn.tolim.pudans.extension.contract.index.query.EqualCondition;
import cn.tolim.pudans.extension.contract.index.query.InCondition;
import cn.tolim.pudans.extension.contract.index.query.OrCondition;
import cn.tolim.pudans.extension.contract.index.query.StringContainsCondition;
import cn.tolim.pudans.core.rbac.group.Groups;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UserEndpointListOptionsTest {

    private final Groups groups = mock(Groups.class);

    private final UserEndpoint endpoint = new UserEndpoint(
            null, null, null, groups, null, null, null, null, null, null, null, null, null);

    @Test
    void pushesActiveStatusDownAsDisabledFalse() throws Exception {
        var condition = condition(options(null, Set.of("active"), Set.of()));

        assertThat(condition).isEqualTo(new EqualCondition("spec.disabled", false));
    }

    @Test
    void pushesDisabledStatusDownAsDisabledTrue() throws Exception {
        var condition = condition(options(null, Set.of("disabled"), Set.of()));

        assertThat(condition).isEqualTo(new EqualCondition("spec.disabled", true));
    }

    @Test
    void dropsTheStatusConditionWhenBothStatusesAreRequested() throws Exception {
        var condition = condition(options(null, Set.of("active", "disabled"), Set.of()));

        assertThat(condition).isInstanceOf(EmptyCondition.class);
    }

    @Test
    void rejectsAnUnknownStatusWithoutQueryingTheStore() throws Exception {
        StepVerifier.create(options(null, Set.of("deleted"), Set.of())).verifyComplete();
    }

    @Test
    void pushesKeywordDownAsACaseInsensitiveMatchOnDisplayNameAndEmail() throws Exception {
        var condition = condition(options("Ali", Set.of(), Set.of()));

        assertThat(condition)
                .isEqualTo(new OrCondition(
                        new StringContainsCondition("spec.displayName", "Ali"),
                        new StringContainsCondition("spec.email", "Ali")));
    }

    @Test
    void pushesUserGroupMembersDownAsANameFilter() throws Exception {
        when(groups.listUsernamesOfGroups(Set.of("operators")))
                .thenReturn(Flux.just("alice", "bob"));

        var condition = condition(options(null, Set.of(), Set.of("operators")));

        assertThat(condition).isInstanceOf(InCondition.class);
        var in = (InCondition) condition;
        assertThat(in.indexName()).isEqualTo("metadata.name");
        assertThat(in.keys()).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void rejectsAUserGroupWithoutMembersWithoutQueryingTheStore() throws Exception {
        when(groups.listUsernamesOfGroups(Set.of("empty"))).thenReturn(Flux.empty());

        StepVerifier.create(options(null, Set.of(), Set.of("empty"))).verifyComplete();
    }

    @Test
    void combinesEveryFilterIntoOneCondition() throws Exception {
        when(groups.listUsernamesOfGroups(Set.of("operators")))
                .thenReturn(Flux.just("alice"));

        var condition = condition(options("Ai", Set.of("active"), Set.of("operators")));

        assertThat(condition.toString())
                .contains("spec.disabled = false", "spec.displayName CONTAINS Ai", "metadata.name = alice");
    }

    @SuppressWarnings("unchecked")
    private Mono<ListOptions> options(String keyword, Set<String> statuses, Set<String> userGroups)
            throws Exception {
        Method method = UserEndpoint.class.getDeclaredMethod(
                "listOptionsOf", ListOptions.class, String.class, Set.class, Set.class);
        method.setAccessible(true);
        return (Mono<ListOptions>) method.invoke(endpoint, ListOptions.builder().build(), keyword, statuses, userGroups);
    }

    private cn.tolim.pudans.extension.contract.index.query.Condition condition(Mono<ListOptions> options) {
        return options.blockOptional().orElseThrow().toCondition();
    }
}
