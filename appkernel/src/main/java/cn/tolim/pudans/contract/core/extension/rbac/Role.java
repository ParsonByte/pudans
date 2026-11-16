package cn.tolim.pudans.contract.core.extension.rbac;

import static cn.tolim.pudans.contract.core.extension.rbac.Role.GROUP;
import static cn.tolim.pudans.contract.core.extension.rbac.Role.KIND;
import static cn.tolim.pudans.contract.core.extension.rbac.Role.VERSION;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static java.util.Arrays.compare;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;


@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(group = GROUP, version = VERSION, kind = KIND, plural = "roles", singular = "role")
public class Role extends AbstractExtension {
    public static final String ROLE_DEPENDENCY_RULES = "rbac.authorization.pudans.tolim.cn/dependency-rules";
    public static final String ROLE_AGGREGATE_LABEL_PREFIX = "rbac.authorization.pudans.tolim.cn/aggregate-to-";
    public static final String ROLE_DEPENDENCIES_ANNO = "rbac.authorization.pudans.tolim.cn/dependencies";
    public static final String UI_PERMISSIONS_ANNO = "rbac.authorization.pudans.tolim.cn/ui-permissions";

    /** Mirrors Halo's rbac.authorization.halo.run/disallow-access-console. */
    public static final String DISALLOW_ACCESS_CONSOLE_ANNO =
            "rbac.authorization.pudans.tolim.cn/disallow-access-console";

    public static final String SYSTEM_RESERVED_LABELS = "rbac.authorization.pudans.tolim.cn/system-reserved";
    public static final String HIDDEN_LABEL_NAME = "pudans.tolim.cn/hidden";
    public static final String TEMPLATE_LABEL_NAME = "pudans.tolim.cn/role-template";

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "Role";


    @Schema(requiredMode = REQUIRED)
    List<PolicyRule> rules;


    @Getter
    @EqualsAndHashCode
    public static class PolicyRule implements Comparable<PolicyRule> {


        final String[] apiGroups;


        final String[] resources;


        final String[] resourceNames;


        final String[] nonResourceURLs;


        final String[] verbs;

        public PolicyRule() {
            this(null, null, null, null, null);
        }

        public PolicyRule(
                String[] apiGroups,
                String[] resources,
                String[] resourceNames,
                String[] nonResourceURLs,
                String[] verbs) {
            this.apiGroups = nullElseEmpty(apiGroups);
            this.resources = nullElseEmpty(resources);
            this.resourceNames = nullElseEmpty(resourceNames);
            this.nonResourceURLs = nullElseEmpty(nonResourceURLs);
            this.verbs = nullElseEmpty(verbs);
        }

        String[] nullElseEmpty(String @Nullable ... items) {
            if (items == null) {
                return new String[] {};
            }
            return items;
        }

        @Override
        public int compareTo(PolicyRule other) {
            int result = compare(apiGroups, other.apiGroups);
            if (result != 0) {
                return result;
            }
            result = compare(resources, other.resources);
            if (result != 0) {
                return result;
            }
            result = compare(resourceNames, other.resourceNames);
            if (result != 0) {
                return result;
            }
            result = compare(nonResourceURLs, other.nonResourceURLs);
            if (result != 0) {
                return result;
            }
            result = compare(verbs, other.verbs);
            return result;
        }

        public static class Builder {

            String[] apiGroups;


            String[] resources;


            String[] resourceNames;


            String[] nonResourceURLs;


            String[] verbs;

            public Builder apiGroups(String... apiGroups) {
                this.apiGroups = apiGroups;
                return this;
            }

            public Builder resources(String... resources) {
                this.resources = resources;
                return this;
            }

            public Builder resourceNames(String... resourceNames) {
                this.resourceNames = resourceNames;
                return this;
            }

            public Builder nonResourceURLs(String... nonResourceURLs) {
                this.nonResourceURLs = nonResourceURLs;
                return this;
            }

            public Builder verbs(String... verbs) {
                this.verbs = verbs;
                return this;
            }

            public PolicyRule build() {
                return new PolicyRule(apiGroups, resources, resourceNames, nonResourceURLs, verbs);
            }
        }
    }
}
