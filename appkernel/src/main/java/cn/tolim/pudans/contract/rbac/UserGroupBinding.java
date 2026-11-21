package cn.tolim.pudans.contract.rbac;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.api.AbstractExtension;
import cn.tolim.pudans.extension.api.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;


@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = UserGroupBinding.GROUP,
        version = UserGroupBinding.VERSION,
        kind = UserGroupBinding.KIND,
        plural = "usergroupbindings",
        singular = "usergroupbinding")
public class UserGroupBinding extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "UserGroupBinding";


    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Subject> subjects = new LinkedList<>();


    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private RoleRef roleRef;

    public static UserGroupBinding membership(String username, String groupName) {
        var binding = new UserGroupBinding();
        binding.setSubjects(new LinkedList<>(List.of(Subject.user(username))));
        binding.setRoleRef(RoleRef.userGroup(groupName));
        return binding;
    }

    public static UserGroupBinding grant(String groupName, String roleName) {
        var binding = new UserGroupBinding();
        binding.setSubjects(new LinkedList<>(List.of(Subject.userGroup(groupName))));
        binding.setRoleRef(RoleRef.role(roleName));
        return binding;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subject {


        private String kind;


        private String name;


        private String apiGroup;

        public static Subject user(String name) {
            return new Subject(User.KIND, name, User.GROUP);
        }

        public static Subject userGroup(String name) {
            return new Subject(UserGroup.KIND, name, UserGroup.GROUP);
        }

        @Override
        public String toString() {
            return apiGroup + "/" + kind + "/" + name;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleRef {


        private String kind;


        private String name;


        private String apiGroup;

        public static RoleRef userGroup(String name) {
            return new RoleRef(UserGroup.KIND, name, UserGroup.GROUP);
        }

        public static RoleRef role(String name) {
            return new RoleRef(Role.KIND, name, Role.GROUP);
        }

        @Override
        public String toString() {
            return apiGroup + "/" + kind + "/" + name;
        }
    }
}
