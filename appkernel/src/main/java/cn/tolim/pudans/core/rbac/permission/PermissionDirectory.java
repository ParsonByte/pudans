package cn.tolim.pudans.core.rbac.permission;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.rbac.Role.PolicyRule;
import cn.tolim.pudans.extension.contract.ConfigMap;
import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.ExtensionUtil;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.MetadataUtil;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Watcher;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionDirectory {

    private static final Duration TTL = Duration.ofMinutes(5);

    private static final String UI_RESOURCE_LABELS = "ui-resource-labels";

    private final ReactiveExtensionClient client;

    private volatile PermissionCatalog snapshot;

    private volatile long loadedAt;

    public record PermissionCatalog(
            List<Workspace> workspaces, Map<String, String> resourceLabels) {}

    public record Workspace(
            String id,
            String slug,
            String name,
            String plan,
            String icon,
            int order,
            List<Menu> menus) {}

    public record Menu(
            String key,
            String label,
            String description,
            String icon,
            String group,
            String groupKey,
            String parent,
            String parentIcon,
            String component,
            int order,
            boolean publicMenu,
            Level view,
            Level manage,
            List<Operation> operations) {}

    public record Level(
            String template, String permission, String capability, List<String> dependencies, List<PolicyRule> rules) {}

    public record Operation(
            String key,
            String label,
            String description,
            String type,
            String template,
            String permission,
            int order,
            List<String> dependencies,
            List<PolicyRule> rules) {}

    public record Navigation(List<NavigationWorkspace> workspaces) {}

    public record NavigationWorkspace(
            String id,
            String slug,
            String name,
            String plan,
            String icon,
            int order,
            List<NavigationMenu> menus) {}

    public record NavigationMenu(
            String key,
            String label,
            String icon,
            String group,
            String groupKey,
            String parent,
            String parentIcon,
            String component,
            int order,
            List<NavigationOperation> operations) {}

    public record NavigationOperation(String key, boolean allowed) {}

    // 监听角色与配置变化，使导航目录及时失效。
    @PostConstruct
    void registerWatcher() {
        client.watch(new Watcher() {
            // 新增资源可能影响目录，立即失效缓存。
            @Override
            public void onAdd(Extension extension) {
                invalidate(extension);
            }

            // 更新资源后失效缓存，下次请求重新加载。
            @Override
            public void onUpdate(Extension oldExtension, Extension newExtension) {
                invalidate(newExtension);
            }

            // 删除资源后失效缓存，避免返回已移除菜单。
            @Override
            public void onDelete(Extension extension) {
                invalidate(extension);
            }

            @Override
            public void dispose() {}

            // 观察者常驻生命周期，始终视为未释放。
            @Override
            public boolean isDisposed() {
                return false;
            }
        });
    }

    // 返回角色模板构建的权限目录，必要时重新加载。
    public Mono<PermissionCatalog> catalog() {
        return snapshot();
    }

    // 按管理权限展开关联的查看与操作权限。
    public Mono<List<String>> expand(List<String> permissions) {
        if (permissions.contains(UiPermissions.ALL_UI_PERMISSIONS)) {
            return Mono.just(List.of(UiPermissions.ALL_UI_PERMISSIONS));
        }
        return snapshot().map(catalog -> {
            var expanded = new LinkedHashSet<>(permissions);
            var changed = true;
            while (changed) {
                changed = false;
                for (var workspace : catalog.workspaces()) {
                    for (var menu : workspace.menus()) {
                        if (menu.manage() == null
                                || menu.manage().permission() == null
                                || !expanded.contains(menu.manage().permission())) {
                            continue;
                        }
                        if (menu.view() != null && menu.view().permission() != null) {
                            changed |= expanded.add(menu.view().permission());
                        }
                        for (var operation : menu.operations()) {
                            if (operation.permission() != null) {
                                changed |= expanded.add(operation.permission());
                            }
                        }
                    }
                }
            }
            return List.copyOf(expanded);
        });
    }

    // 判断权限集合是否具备指定界面能力。
    public Mono<Boolean> hasCapability(List<String> permissions, String capability) {
        if (permissions.contains(UiPermissions.ALL_UI_PERMISSIONS)) {
            return Mono.just(true);
        }
        return snapshot().map(catalog -> {
            for (var workspace : catalog.workspaces()) {
                for (var menu : workspace.menus()) {
                    if (matchesCapability(menu.view(), permissions, capability)
                            || matchesCapability(menu.manage(), permissions, capability)) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    // 检查层级权限是否命中目标能力。
    private static boolean matchesCapability(Level level, List<String> permissions, String capability) {
        return level != null
                && capability.equals(level.capability())
                && level.permission() != null
                && permissions.contains(level.permission());
    }

    // 按权限裁剪工作区与菜单，生成导航结构。
    public Mono<Navigation> navigation(List<String> permissions) {
        return snapshot().map(catalog -> {
            var granted = new HashSet<>(permissions);
            var allGranted = granted.contains(UiPermissions.ALL_UI_PERMISSIONS);
            var workspaces = new ArrayList<NavigationWorkspace>();
            for (var workspace : catalog.workspaces()) {
                var menus = workspace.menus().stream()
                        .filter(menu -> allGranted || isAccessible(menu, granted))
                        .sorted(Comparator.comparingInt(Menu::order))
                        .map(menu -> new NavigationMenu(
                                menu.key(),
                                menu.label(),
                                menu.icon(),
                                menu.group(),
                                menu.groupKey(),
                                menu.parent(),
                                menu.parentIcon(),
                                menu.component(),
                                menu.order(),
                                menu.operations().stream()
                                        .map(operation -> new NavigationOperation(
                                                operation.key(),
                                                allGranted
                                                        || (operation.permission() != null
                                                                && granted.contains(operation.permission()))))
                                        .toList()))
                        .toList();
                if (menus.isEmpty()) {
                    continue;
                }
                workspaces.add(new NavigationWorkspace(
                        workspace.id(),
                        workspace.slug(),
                        workspace.name(),
                        workspace.plan(),
                        workspace.icon(),
                        workspace.order(),
                        menus));
            }
            return new Navigation(workspaces);
        });
    }

    // 公开菜单或任一权限命中即可访问。
    private static boolean isAccessible(Menu menu, Set<String> permissions) {
        if (menu.publicMenu()) {
            return true;
        }
        if (menu.view() != null
                && menu.view().permission() != null
                && permissions.contains(menu.view().permission())) {
            return true;
        }
        return menu.manage() != null
                && menu.manage().permission() != null
                && permissions.contains(menu.manage().permission());
    }

    // 角色或配置变化后让目录快照失效。
    private void invalidate(Extension extension) {
        if (extension != null
                && (Role.KIND.equals(extension.getKind())
                        || ConfigMap.KIND.equals(extension.getKind()))) {
            snapshot = null;
            loadedAt = 0L;
        }
    }

    // 在有效期内复用快照，否则重新加载目录。
    private Mono<PermissionCatalog> snapshot() {
        var current = snapshot;
        if (current != null && System.currentTimeMillis() - loadedAt < TTL.toMillis()) {
            return Mono.just(current);
        }
        return load().doOnNext(catalog -> {
            snapshot = catalog;
            loadedAt = System.currentTimeMillis();
        });
    }

    // 加载角色模板与资源标签配置。
    private Mono<PermissionCatalog> load() {
        var options = ListOptions.builder()
                .labelSelector()
                .eq(Role.TEMPLATE_LABEL_NAME, Boolean.TRUE.toString())
                .end()
                .build();
        var roles = client.listAll(Role.class, options, ExtensionUtil.defaultSort())
                .filter(role -> role.getMetadata() != null
                        && role.getMetadata().getDeletionTimestamp() == null)
                .collectList();
        var resourceLabels = client.fetch(ConfigMap.class, UI_RESOURCE_LABELS)
                .map(ConfigMap::getData)
                .map(data -> data == null ? Map.<String, String>of() : data)
                .defaultIfEmpty(Map.of());
        return Mono.zip(roles, resourceLabels).map(tuple -> buildCatalog(tuple.getT1(), tuple.getT2()));
    }

    // 把角色模板注解装配成工作区、菜单与操作。
    private PermissionCatalog buildCatalog(List<Role> roles, Map<String, String> resourceLabels) {
        var workspaces = new LinkedHashMap<String, WorkspaceBuilder>();
        var menus = new LinkedHashMap<String, MenuBuilder>();
        var operations = new ArrayList<OperationContext>();

        for (var role : roles) {
            var annotations = MetadataUtil.nullSafeAnnotations(role);
            var type = annotations.get(Role.UI_TYPE_ANNO);
            if (!StringUtils.isNotBlank(type)) {
                continue;
            }
            var template = role.getMetadata().getName();
            var dependencies = parseList(annotations.get(Role.ROLE_DEPENDENCIES_ANNO));
            var rules = role.getRules() == null ? List.<PolicyRule>of() : role.getRules();
            switch (type) {
                case Role.UI_TYPE_WORKSPACE -> {
                    var workspaceId = annotations.get(Role.UI_WORKSPACE_ANNO);
                    if (!StringUtils.isNotBlank(workspaceId)) {
                        continue;
                    }
                    var workspace = workspaces.computeIfAbsent(workspaceId, WorkspaceBuilder::new);
                    workspace.slug = defaultValue(annotations.get(Role.UI_SLUG_ANNO), workspace.id);
                    workspace.name = defaultValue(annotations.get(Role.UI_LABEL_ANNO), workspaceId);
                    workspace.plan = defaultValue(annotations.get(Role.UI_DESCRIPTION_ANNO), "");
                    workspace.icon = annotations.get(Role.UI_ICON_ANNO);
                    workspace.order = intValue(annotations.get(Role.UI_ORDER_ANNO));
                }
                case Role.UI_TYPE_MENU -> {
                    var menuKey = annotations.get(Role.UI_MENU_ANNO);
                    if (!StringUtils.isNotBlank(menuKey)) {
                        continue;
                    }
                    var menu = menus.computeIfAbsent(menuKey, MenuBuilder::new);
                    var workspaceId = annotations.get(Role.UI_WORKSPACE_ANNO);
                    if (StringUtils.isNotBlank(workspaceId)) {
                        menu.workspaceId = workspaceId;
                        workspaces.computeIfAbsent(workspaceId, WorkspaceBuilder::new);
                    }
                    applyMenuMetadata(menu, annotations);
                    var level = new Level(
                            template,
                            firstPermission(annotations),
                            annotations.get(Role.UI_CAPABILITY_ANNO),
                            dependencies,
                            rules);
                    if (Role.UI_ACTION_MANAGE.equals(annotations.get(Role.UI_ACTION_ANNO))) {
                        menu.manage = level;
                    } else {
                        menu.view = level;
                    }
                }
                case Role.UI_TYPE_BUTTON, Role.UI_TYPE_TABLE -> {
                    var menuKey = annotations.get(Role.UI_MENU_ANNO);
                    if (!StringUtils.isNotBlank(menuKey)) {
                        continue;
                    }
                    var permission = firstPermission(annotations);
                    operations.add(new OperationContext(
                            menuKey,
                            new Operation(
                                    defaultValue(
                                            annotations.get(Role.UI_OPERATION_ANNO),
                                            defaultValue(permission, template)),
                                    defaultValue(annotations.get(Role.UI_LABEL_ANNO), template),
                                    defaultValue(annotations.get(Role.UI_DESCRIPTION_ANNO), ""),
                                    type,
                                    template,
                                    permission,
                                    intValue(annotations.get(Role.UI_ORDER_ANNO)),
                                    dependencies,
                                    rules)));
                }
                default -> {
                }
            }
        }

        for (var operation : operations) {
            var menu = menus.get(operation.menuKey());
            if (menu != null) {
                menu.operations.add(operation.operation());
            }
        }

        var catalogWorkspaces = workspaces.values().stream()
                .sorted(Comparator.comparingInt(workspace -> workspace.order))
                .map(workspace -> new Workspace(
                        workspace.id,
                        workspace.slug,
                        workspace.name,
                        workspace.plan,
                        workspace.icon,
                        workspace.order,
                        menus.values().stream()
                                .filter(menu -> Objects.equals(workspace.id, menu.workspaceId))
                                .sorted(Comparator.comparingInt(menu -> menu.order))
                                .map(menu -> menu.build())
                                .toList()))
                .toList();
        return new PermissionCatalog(catalogWorkspaces, resourceLabels);
    }

    // 用注解覆盖菜单的可选展示字段。
    private static void applyMenuMetadata(MenuBuilder menu, Map<String, String> annotations) {
        var label = annotations.get(Role.UI_LABEL_ANNO);
        if (StringUtils.isNotBlank(label)) {
            menu.label = label;
        }
        var description = annotations.get(Role.UI_DESCRIPTION_ANNO);
        if (StringUtils.isNotBlank(description)) {
            menu.description = description;
        }
        var icon = annotations.get(Role.UI_ICON_ANNO);
        if (StringUtils.isNotBlank(icon)) {
            menu.icon = icon;
        }
        var group = annotations.get(Role.UI_GROUP_ANNO);
        if (StringUtils.isNotBlank(group)) {
            menu.group = group;
        }
        var groupKey = annotations.get(Role.UI_GROUP_KEY_ANNO);
        if (StringUtils.isNotBlank(groupKey)) {
            menu.groupKey = groupKey;
        }
        var parent = annotations.get(Role.UI_PARENT_ANNO);
        if (StringUtils.isNotBlank(parent)) {
            menu.parent = parent;
        }
        var parentIcon = annotations.get(Role.UI_PARENT_ICON_ANNO);
        if (StringUtils.isNotBlank(parentIcon)) {
            menu.parentIcon = parentIcon;
        }
        var component = annotations.get(Role.UI_COMPONENT_ANNO);
        if (StringUtils.isNotBlank(component)) {
            menu.component = component;
        }
        var order = annotations.get(Role.UI_ORDER_ANNO);
        if (StringUtils.isNotBlank(order)) {
            menu.order = intValue(order);
        }
        if (Boolean.parseBoolean(annotations.get(Role.UI_PUBLIC_ANNO))) {
            menu.publicMenu = true;
        }
    }

    // 取注解中的第一个界面权限作为主权限。
    private static String firstPermission(Map<String, String> annotations) {
        var permissions = parseList(annotations.get(Role.UI_PERMISSIONS_ANNO));
        return permissions.stream().filter(StringUtils::isNotBlank).findFirst().orElse(null);
    }

    // 解析注解中的数组，解析失败按空列表处理。
    private static List<String> parseList(String raw) {
        if (StringUtils.isBlank(raw)) {
            return List.of();
        }
        try {
            List<String> parsed = JsonUtils.jsonToObject(raw, new TypeReference<>() {});
            if (parsed == null) {
                return List.of();
            }
            return parsed.stream().filter(StringUtils::isNotBlank).toList();
        } catch (Exception e) {
            log.warn("无法解析角色模板注解：{}", raw, e);
            return List.of();
        }
    }

    // 注解值非空时采用，否则回退到默认值。
    private static String defaultValue(String value, String fallback) {
        return StringUtils.isNotBlank(value) ? value : fallback;
    }

    // 解析整型注解，非法或缺失时归零。
    private static int intValue(String value) {
        if (StringUtils.isBlank(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record OperationContext(String menuKey, Operation operation) {}

    private static final class WorkspaceBuilder {
        private final String id;

        private String slug;

        private String name;

        private String plan = "";

        private String icon;

        private int order;

        // 以工作区标识初始化构建器，缺省名称同标识。
        private WorkspaceBuilder(String id) {
            this.id = id;
            this.slug = id;
            this.name = id;
        }
    }

    private static final class MenuBuilder {
        private final String key;

        private String workspaceId;

        private String label;

        private String description = "";

        private String icon;

        private String group;

        private String groupKey;

        private String parent;

        private String parentIcon;

        private String component;

        private int order;

        private boolean publicMenu;

        private Level view;

        private Level manage;

        private final List<Operation> operations = new ArrayList<>();

        // 以菜单键初始化构建器，标签缺省同键。
        private MenuBuilder(String key) {
            this.key = key;
            this.label = key;
        }

        // 生成按顺序排列操作的不可变菜单对象。
        private Menu build() {
            var sortedOperations = operations.stream()
                    .sorted(Comparator.comparingInt(Operation::order))
                    .toList();
            return new Menu(
                    key,
                    label,
                    description,
                    icon,
                    group,
                    StringUtils.isNotBlank(groupKey) ? groupKey : defaultValue(group, key),
                    parent,
                    parentIcon,
                    component,
                    order,
                    publicMenu,
                    view,
                    manage,
                    sortedOperations);
        }
    }
}
