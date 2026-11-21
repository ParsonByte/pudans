package cn.tolim.pudans.contract.rbac;

import static cn.tolim.pudans.contract.rbac.RoleBinding.GROUP;
import static cn.tolim.pudans.contract.rbac.RoleBinding.KIND;
import static cn.tolim.pudans.contract.rbac.RoleBinding.VERSION;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.ExtensionOperator;
import cn.tolim.pudans.contract.extension.GVK;
import cn.tolim.pudans.contract.extension.Metadata;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.util.StringUtils;


@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(group = GROUP, version = VERSION, kind = KIND, plural = "rolebindings", singular = "rolebinding")
public class RoleBinding extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";

    public static final String VERSION = "v1alpha1";

    public static final String KIND = "RoleBinding";


    List<Subject> subjects;


    RoleRef roleRef;

    public static RoleBinding create(String username, String roleName) {
        var metadata = new Metadata();
        metadata.setName(String.join("-", username, roleName, "binding"));

        var roleRef = new RoleRef();
        roleRef.setKind(Role.KIND);
        roleRef.setName(roleName);
        roleRef.setApiGroup(Role.GROUP);

        var subject = new Subject();
        subject.setKind(User.KIND);
        subject.setName(username);
        subject.setApiGroup(User.GROUP);

        var binding = new RoleBinding();
        binding.setMetadata(metadata);
        binding.setRoleRef(roleRef);


        var subjects = new LinkedList<Subject>();
        subjects.add(subject);

        binding.setSubjects(subjects);
        return binding;
    }

    public static Predicate<RoleBinding> containsUser(String username) {
        return ExtensionOperator.<RoleBinding>isNotDeleted()
                .and(binding -> binding.getSubjects().stream().anyMatch(Subject.isUser(username)));
    }

    public static Predicate<RoleBinding> containsUser(Set<String> usernames) {
        return ExtensionOperator.<RoleBinding>isNotDeleted()
                .and(binding -> binding.getSubjects().stream().anyMatch(Subject.containsUser(usernames)));
    }

    @Data
    public static class RoleRef {


        String kind;


        String name;


        String apiGroup;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subject {


        String kind;


        String name;


        String apiGroup;

        public static Predicate<Subject> isUser(String username) {
            return subject -> User.KIND.equals(subject.getKind())
                    && User.GROUP.equals(subject.getApiGroup())
                    && username.equals(subject.getName());
        }

        public static Predicate<Subject> containsUser(Set<String> usernames) {
            return subject -> User.KIND.equals(subject.getKind())
                    && User.GROUP.equals(subject.apiGroup)
                    && usernames.contains(subject.getName());
        }

        @Override
        public String toString() {
            if (StringUtils.hasText(apiGroup)) {
                return apiGroup + "/" + kind + "/" + name;
            }
            return kind + "/" + name;
        }
    }
}
