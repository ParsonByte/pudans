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
import cn.tolim.pudans.extension.api.ConfigMap;
import cn.tolim.pudans.extension.api.Extension;
import cn.tolim.pudans.extension.api.ExtensionUtil;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.MetadataUtil;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.extension.api.Watcher;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
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

    public record Workspace(String id, String name, String plan, String icon, int order, List<Menu> menus) {}

    public record Menu(
            String key,
            String label,
            String description,
            String path,
            String icon,
            String group,
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
            String id, String name, String plan, String icon, int order, String homePath, List<NavigationMenu> menus) {}

    public record NavigationMenu(
            String key,
            String label,
            String path,
            String icon,
            String group,
            String parent,
            String parentIcon,
            String component,
            int order,
            List<NavigationOperation> operations) {}

    public record NavigationOperation(String key, boolean allowed) {}

    @PostConstruct
    void registerWatcher() {
        client.watch(new Watcher() {
            @Override
            public void onAdd(Extension extension) {
                invalidate(extension);
            }

            @Override
            public void onUpdate(Extension oldExtension, Extension newExtension) {
                invalidate(newExtension);
            }

            @Override
            public void onDelete(Extension extension) {
                invalidate(extension);
            }

            @Override
            public void dispose() {}

            @Override
            public boolean isDisposed() {
                return false;
            }
        });
    }

    public Mono<PermissionCatalog> catalog() {
        return snapshot();
    }

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

    private static boolean matchesCapability(Level level, List<String> permissions, String capability) {
        return level != null
                && capability.equals(level.capability())
                && level.permission() != null
                && permissions.contains(level.permission());
    }

    public Mono<Navigation> navigation(List<String> permissions) {        return snapshot().map(catalog -> {
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
                                menu.path(),
                                menu.icon(),
                                menu.group(),
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
                        workspace.name(),
                        workspace.plan(),
                        workspace.icon(),
                        workspace.order(),
                        menus.getFirst().path(),
                        menus));
            }
            return new Navigation(workspaces);
        });
    }

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

    private void invalidate(Extension extension) {
        if (extension != null
                && (Role.KIND.equals(extension.getKind())
                        || ConfigMap.KIND.equals(extension.getKind()))) {
            snapshot = null;
            loadedAt = 0L;
        }
    }

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

    private static void applyMenuMetadata(MenuBuilder menu, Map<String, String> annotations) {
        var label = annotations.get(Role.UI_LABEL_ANNO);
        if (StringUtils.isNotBlank(label)) {
            menu.label = label;
        }
        var description = annotations.get(Role.UI_DESCRIPTION_ANNO);
        if (StringUtils.isNotBlank(description)) {
            menu.description = description;
        }
        var path = annotations.get(Role.UI_PATH_ANNO);
        if (StringUtils.isNotBlank(path)) {
            menu.path = path;
        }
        var icon = annotations.get(Role.UI_ICON_ANNO);
        if (StringUtils.isNotBlank(icon)) {
            menu.icon = icon;
        }
        var group = annotations.get(Role.UI_GROUP_ANNO);
        if (StringUtils.isNotBlank(group)) {
            menu.group = group;
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

    private static String firstPermission(Map<String, String> annotations) {
        var permissions = parseList(annotations.get(Role.UI_PERMISSIONS_ANNO));
        return permissions.stream().filter(StringUtils::isNotBlank).findFirst().orElse(null);
    }

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

    private static String defaultValue(String value, String fallback) {
        return StringUtils.isNotBlank(value) ? value : fallback;
    }

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

        private String name;

        private String plan = "";

        private String icon;

        private int order;

        private WorkspaceBuilder(String id) {
            this.id = id;
            this.name = id;
        }
    }

    private static final class MenuBuilder {
        private final String key;

        private String workspaceId;

        private String label;

        private String description = "";

        private String path;

        private String icon;

        private String group;

        private String parent;

        private String parentIcon;

        private String component;

        private int order;

        private boolean publicMenu;

        private Level view;

        private Level manage;

        private final List<Operation> operations = new ArrayList<>();

        private MenuBuilder(String key) {
            this.key = key;
            this.label = key;
        }

        private Menu build() {
            var sortedOperations = operations.stream()
                    .sorted(Comparator.comparingInt(Operation::order))
                    .toList();
            return new Menu(
                    key,
                    label,
                    description,
                    path,
                    icon,
                    group,
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
