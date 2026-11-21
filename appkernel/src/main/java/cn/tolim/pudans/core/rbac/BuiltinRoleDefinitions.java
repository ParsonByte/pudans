package cn.tolim.pudans.core.rbac;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.extension.contract.AbstractExtension;
import cn.tolim.pudans.extension.contract.MetadataUtil;
import cn.tolim.pudans.extension.contract.Unstructured;
import cn.tolim.pudans.infra.utils.YamlUnstructuredLoader;

@Component
public class BuiltinRoleDefinitions {

    private static final String ROLE_LOCATION = "classpath:/extensions/";

    private static final String[] RESOURCE_PATTERNS = {
        "classpath:/extensions/*.yaml",
        "classpath:/extensions/*.yml",
        "classpath:/extensions/**/*.yaml",
        "classpath:/extensions/**/*.yml",
    };

    private final List<Role> allRoles;

    private final List<Role> builtinRoles;

    private final List<UserGroup> builtinGroups;

    // 启动时加载扩展目录中的内置角色与用户组定义。
    public BuiltinRoleDefinitions() {
        var roles = new ArrayList<Role>();
        var builtinRoles = new ArrayList<Role>();
        var builtinGroups = new ArrayList<UserGroup>();
        for (var document : loadDocuments()) {
            if (Role.KIND.equals(document.getKind())) {
                var role = Unstructured.OBJECT_MAPPER.convertValue(document.getData(), Role.class);
                roles.add(role);
                if (isBuiltin(role)) {
                    builtinRoles.add(role);
                }
            } else if (UserGroup.KIND.equals(document.getKind())) {
                var group = Unstructured.OBJECT_MAPPER.convertValue(document.getData(), UserGroup.class);
                if (isBuiltin(group)) {
                    builtinGroups.add(group);
                }
            }
        }
        if (builtinRoles.isEmpty()) {
            throw new IllegalStateException(
                    "未在 " + ROLE_LOCATION + " 下找到内置角色定义，内置角色必须带有 ["
                            + BuiltinEntities.BUILTIN_LABEL + "] 标签。");
        }
        this.allRoles = List.copyOf(roles);
        this.builtinRoles = List.copyOf(builtinRoles);
        this.builtinGroups = List.copyOf(builtinGroups);
    }

    // 返回启动时加载的内置角色定义。
    public List<Role> builtinRoles() {
        return builtinRoles;
    }

    // 按名称查找内置角色定义。
    public Optional<Role> role(String name) {
        return builtinRoles.stream()
                .filter(role -> Objects.equals(name, role.getMetadata().getName()))
                .findFirst();
    }

    // 返回启动时加载的内置用户组定义。
    public List<UserGroup> builtinGroups() {
        return builtinGroups;
    }

    // 按名称查找内置用户组定义。
    public Optional<UserGroup> group(String name) {
        return builtinGroups.stream()
                .filter(group -> Objects.equals(name, group.getMetadata().getName()))
                .findFirst();
    }

    // 返回扩展目录中的全部角色定义。
    public List<Role> allRoles() {
        return allRoles;
    }

    // 扫描扩展目录并按文件名排序加载全部文档。
    private static List<Unstructured> loadDocuments() {
        var resolver = new PathMatchingResourcePatternResolver();
        var resources = new ArrayList<Resource>();
        try {
            for (var pattern : RESOURCE_PATTERNS) {
                resources.addAll(List.of(resolver.getResources(pattern)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法加载扩展资源：" + ROLE_LOCATION, e);
        }
        resources.sort(Comparator.comparing(resource -> Objects.toString(resource.getFilename(), "")));
        return new YamlUnstructuredLoader(resources.toArray(Resource[]::new)).load();
    }

    // 通过内置标签判断资源是否受系统保护。
    private static boolean isBuiltin(AbstractExtension extension) {
        return extension.getMetadata() != null
                && Boolean.parseBoolean(
                        MetadataUtil.nullSafeLabels(extension).get(BuiltinEntities.BUILTIN_LABEL));
    }
}
