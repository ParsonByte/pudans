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
import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.MetadataUtil;
import cn.tolim.pudans.contract.extension.Unstructured;
import cn.tolim.pudans.core.infra.utils.YamlUnstructuredLoader;

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

    public List<Role> builtinRoles() {
        return builtinRoles;
    }

    public Optional<Role> role(String name) {
        return builtinRoles.stream()
                .filter(role -> Objects.equals(name, role.getMetadata().getName()))
                .findFirst();
    }

    public List<UserGroup> builtinGroups() {
        return builtinGroups;
    }

    public Optional<UserGroup> group(String name) {
        return builtinGroups.stream()
                .filter(group -> Objects.equals(name, group.getMetadata().getName()))
                .findFirst();
    }

    public List<Role> allRoles() {
        return allRoles;
    }

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

    private static boolean isBuiltin(AbstractExtension extension) {
        return extension.getMetadata() != null
                && Boolean.parseBoolean(
                        MetadataUtil.nullSafeLabels(extension).get(BuiltinEntities.BUILTIN_LABEL));
    }
}
