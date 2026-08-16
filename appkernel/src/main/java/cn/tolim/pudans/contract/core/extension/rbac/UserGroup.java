package cn.tolim.pudans.contract.core.extension.rbac;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;


@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = UserGroup.GROUP,
        version = UserGroup.VERSION,
        kind = UserGroup.KIND,
        plural = "usergroups",
        singular = "usergroup")
public class UserGroup extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "UserGroup";


    @Schema(requiredMode = REQUIRED)
    private String displayName;


    private String description;


    private List<RoleRef> roleRefs = new ArrayList<>();


    @Data
    public static class RoleRef {


        private String kind = Role.KIND;


        private String name;


        private String apiGroup = Role.GROUP;
    }
}
