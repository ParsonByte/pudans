package cn.tolim.pudans.core.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.rbac.resource.Role.PolicyRule;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyRuleCoverageTest {

    @Test
    void wildcardGrantCoversSpecificRequest() {
        var granted = rule(new String[] {"*"}, new String[] {"*"}, null, null, new String[] {"*"});
        var requested = rule(
                new String[] {"api.pudans.tolim.cn"},
                new String[] {"users"},
                null,
                null,
                new String[] {"get"});

        assertThat(PolicyRuleCoverage.covers(List.of(granted), requested)).isTrue();
    }

    @Test
    void specificGrantDoesNotCoverWildcardVerb() {
        var granted = rule(
                new String[] {"api.pudans.tolim.cn"},
                new String[] {"users"},
                null,
                null,
                new String[] {"get", "list"});
        var requested = rule(
                new String[] {"api.pudans.tolim.cn"},
                new String[] {"users"},
                null,
                null,
                new String[] {"*"});

        assertThat(PolicyRuleCoverage.covers(List.of(granted), requested)).isFalse();
    }

    @Test
    void specificApiGroupDoesNotCoverWildcardRequest() {
        var granted = rule(
                new String[] {"api.pudans.tolim.cn"},
                new String[] {"users"},
                null,
                null,
                new String[] {"get"});
        var requested = rule(new String[] {"*"}, new String[] {"users"}, null, null, new String[] {"get"});

        assertThat(PolicyRuleCoverage.covers(List.of(granted), requested)).isFalse();
    }

    @Test
    void allResourceNamesCoverSpecificName() {
        var granted = rule(
                new String[] {"api.pudans.tolim.cn"},
                new String[] {"users"},
                null,
                null,
                new String[] {"get"});
        var requested = rule(
                new String[] {"api.pudans.tolim.cn"},
                new String[] {"users"},
                new String[] {"alice"},
                null,
                new String[] {"get"});

        assertThat(PolicyRuleCoverage.covers(List.of(granted), requested)).isTrue();
    }

    @Test
    void specificResourceNameDoesNotCoverAllNames() {
        var granted = rule(
                new String[] {"api.pudans.tolim.cn"},
                new String[] {"users"},
                new String[] {"alice"},
                null,
                new String[] {"get"});
        var requested = rule(
                new String[] {"api.pudans.tolim.cn"},
                new String[] {"users"},
                null,
                null,
                new String[] {"get"});

        assertThat(PolicyRuleCoverage.covers(List.of(granted), requested)).isFalse();
    }

    @Test
    void wildcardResourceNameCoversAllNames() {
        var granted = rule(
                new String[] {"api.pudans.tolim.cn"},
                new String[] {"users"},
                new String[] {"*"},
                null,
                new String[] {"get"});
        var requested = rule(
                new String[] {"api.pudans.tolim.cn"},
                new String[] {"users"},
                null,
                null,
                new String[] {"get"});

        assertThat(PolicyRuleCoverage.covers(List.of(granted), requested)).isTrue();
    }

    @Test
    void nonResourcePrefixGrantCoversPath() {
        var granted = rule(null, null, null, new String[] {"/api/*"}, new String[] {"get"});
        var requested =
                rule(null, null, null, new String[] {"/api/v1/users"}, new String[] {"get"});

        assertThat(PolicyRuleCoverage.covers(List.of(granted), requested)).isTrue();
    }

    @Test
    void nonResourcePrefixGrantDoesNotCoverWildcardRequest() {
        var granted = rule(null, null, null, new String[] {"/api/*"}, new String[] {"get"});
        var requested = rule(null, null, null, new String[] {"*"}, new String[] {"get"});

        assertThat(PolicyRuleCoverage.covers(List.of(granted), requested)).isFalse();
    }

    @Test
    void emptyRequestedRuleIsAlwaysCovered() {
        assertThat(PolicyRuleCoverage.covers(List.of(), new PolicyRule())).isTrue();
    }

    private static PolicyRule rule(
            String[] apiGroups,
            String[] resources,
            String[] resourceNames,
            String[] nonResourceURLs,
            String[] verbs) {
        return new PolicyRule(apiGroups, resources, resourceNames, nonResourceURLs, verbs);
    }
}
