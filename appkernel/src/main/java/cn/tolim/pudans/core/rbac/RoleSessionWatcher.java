package cn.tolim.pudans.core.rbac;

import cn.tolim.pudans.contract.rbac.RoleBinding;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.contract.rbac.UserGroupBinding;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Watcher;
import cn.tolim.pudans.infra.utils.ReactiveUtils;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

// 授权关系变化时使受影响用户的会话立即失效，避免旧角色继续生效。
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleSessionWatcher {

    private final ReactiveExtensionClient client;

    private final Groups groups;

    private final SessionInvalidator sessionInvalidator;

    // 只监听角色、用户组和绑定关系，规则内容变化由每请求解析处理。
    @PostConstruct
    void register() {
        client.watch(new Watcher() {
            // 新增授权关系后失效相关会话。
            @Override
            public void onAdd(Extension extension) {
                handle(extension);
            }

            // 更新授权关系后失效相关会话。
            @Override
            public void onUpdate(Extension oldExtension, Extension newExtension) {
                handle(newExtension);
            }

            // 删除授权关系后失效相关会话。
            @Override
            public void onDelete(Extension extension) {
                handle(extension);
            }

            @Override
            public void dispose() {}

            // 观察者常驻，始终返回未释放状态。
            @Override
            public boolean isDisposed() {
                return false;
            }
        });
    }

    // 按资源类型收集受影响用户并统一失效。
    void handle(Extension extension) {
        if (extension == null) {
            return;
        }
        try {
            List<String> usernames = new ArrayList<>();
            List<String> groupNames = new ArrayList<>();
            switch (extension) {
                case RoleBinding binding -> collectSubjects(binding.getSubjects(), usernames, groupNames);
                case UserGroupBinding binding ->
                    collectSubjects(binding.getSubjects(), usernames, groupNames);
                case UserGroup group -> groupNames.add(group.getMetadata().getName());
                default -> {
                    return;
                }
            }
            usernames.addAll(usernamesOfGroups(groupNames));
            sessionInvalidator
                    .invalidateAll(usernames)
                    .block(ReactiveUtils.DEFAULT_TIMEOUT);
        } catch (RuntimeException error) {
            log.warn("授权变化后失效会话失败", error);
        }
    }

    // 展开用户组主体，得到组内全部用户。
    private Collection<String> usernamesOfGroups(Collection<String> groupNames) {
        var names = groupNames.stream().filter(StringUtils::hasText).distinct().toList();
        if (names.isEmpty()) {
            return List.of();
        }
        return groups.listUsernamesOfGroups(names).collectList().block(ReactiveUtils.DEFAULT_TIMEOUT);
    }

    // 拆分用户与用户组主体，忽略其他类型。
    private static void collectSubjects(
            List<? extends UserGroupBinding.Subject> subjects,
            List<String> usernames,
            List<String> groupNames) {
        if (CollectionUtils.isEmpty(subjects)) {
            return;
        }
        for (var subject : subjects) {
            if (subject == null || !StringUtils.hasText(subject.getName())) {
                continue;
            }
            if (User.KIND.equals(subject.getKind())) {
                usernames.add(subject.getName());
            } else if (UserGroup.KIND.equals(subject.getKind())) {
                groupNames.add(subject.getName());
            }
        }
    }

    // 角色绑定的主体类型与用户组绑定一致，单独重载保持类型安全。
    private static void collectSubjects(
            Collection<RoleBinding.Subject> subjects,
            List<String> usernames,
            List<String> groupNames) {
        if (CollectionUtils.isEmpty(subjects)) {
            return;
        }
        for (var subject : subjects) {
            if (subject == null || !StringUtils.hasText(subject.getName())) {
                continue;
            }
            if (User.KIND.equals(subject.getKind())) {
                usernames.add(subject.getName());
            } else if (UserGroup.KIND.equals(subject.getKind())) {
                groupNames.add(subject.getName());
            }
        }
    }
}
