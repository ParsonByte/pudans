package cn.tolim.pudans.core.user.reconciler;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.addFinalizers;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.isDeleted;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.removeFinalizers;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.security.resource.Device;
import cn.tolim.pudans.contract.security.resource.RememberMeToken;
import cn.tolim.pudans.contract.rbac.resource.UserGroupBinding;
import cn.tolim.pudans.contract.user.resource.User;
import cn.tolim.pudans.contract.user.resource.UserConnection;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.InternalReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.ReactiveReconciler;
import cn.tolim.pudans.contract.extension.controller.Reconciler.Request;
import cn.tolim.pudans.contract.security.PersonalAccessToken;
import cn.tolim.pudans.contract.security.device.DeviceSessions;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.user.AnnouncementReads;
import cn.tolim.pudans.core.user.AvatarStorage;
import cn.tolim.pudans.core.user.HashIds;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserReconciler implements ReactiveReconciler<Request> {

    private static final String FINALIZER_NAME = "user-protection";

    private final InternalReactiveExtensionClient client;

    private final DeviceSessions deviceSessions;

    private final DomainEventPublisher domainEventPublisher;

    private final AvatarStorage avatarStorage;

    private final HashIds hashIds;

    private final AnnouncementReads announcementReads;

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new User()).build();
    }

    @Override
    public Mono<Result> reconcileReactive(Request request) {
        return client.fetch(User.class, request.name())
                .flatMap(user -> {
                    if (isDeleted(user)) {
                        return cleanup(user).then(Mono.defer(() -> {
                            if (!removeFinalizers(user.getMetadata(), Set.of(FINALIZER_NAME))) {
                                return Mono.just(Result.doNotRetry());
                            }
                            return client.updateInternal(user)
                                    .doOnNext(this::publishDeleted)
                                    .thenReturn(Result.doNotRetry());
                        }));
                    }
                    if (!addFinalizers(user.getMetadata(), Set.of(FINALIZER_NAME))) {
                        return Mono.just(Result.doNotRetry());
                    }
                    if (user.getStatus() == null) {
                        user.setStatus(new User.UserStatus());
                    }
                    user.getStatus().setHashId(hashIds.encode(user.getMetadata().getName()));
                    return client.updateInternal(user).thenReturn(Result.doNotRetry());
                })
                .switchIfEmpty(Mono.just(Result.doNotRetry()));
    }

    private Mono<Void> cleanup(User user) {
        var username = user.getMetadata().getName();
        return Mono.when(
                deleteUserAvatar(user),
                deleteUserAnnouncementState(username),
                deleteUserConnections(username),
                deleteUserDevices(username),
                deleteUserRememberMeTokens(username),
                deleteUserPersonalAccessTokens(username),
                deleteUserGroupMemberships(username));
    }

    private Mono<Void> deleteUserAnnouncementState(String username) {
        return announcementReads
                .deleteForUser(username)
                .onErrorResume(error -> {
                    log.warn("无法删除用户 [{}] 的公告状态", username, error);
                    return Mono.empty();
                });
    }

    private Mono<Void> deleteUserAvatar(User user) {
        return avatarStorage
                .deleteForUser(user)
                .onErrorResume(error -> {
                    log.warn("无法删除用户 [{}] 的头像", user.getMetadata().getName(), error);
                    return Mono.empty();
                });
    }

    private void publishDeleted(User user) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", user.getMetadata().getName());
        payload.put("uid", user.getSpec().getUid());
        payload.put("email", user.getSpec().getEmail());
        var deletedAt = user.getMetadata().getDeletionTimestamp();
        payload.put("deletedAt", (deletedAt == null ? Instant.now() : deletedAt).toString());
        domainEventPublisher.publish("user.deleted", payload, null);
    }

    private Mono<Void> deleteUserConnections(String username) {
        var listOptions = ListOptions.builder()
                .andQuery(equal("spec.username", username))
                .build();
        return client.listAll(UserConnection.class, listOptions, defaultSort())
                .flatMap(client::delete)
                .then();
    }

    private Mono<Void> deleteUserDevices(String username) {
        var listOptions = ListOptions.builder()
                .andQuery(equal("spec.principalName", username))
                .build();
        return client.listAll(Device.class, listOptions, defaultSort())
                .filter(device -> !isDeleted(device))
                .concatMap(device -> deviceSessions.revoke(username, device.getMetadata().getName()))
                .then();
    }

    private Mono<Void> deleteUserRememberMeTokens(String username) {
        var listOptions = ListOptions.builder()
                .andQuery(equal("spec.username", username))
                .build();
        return client.listAll(RememberMeToken.class, listOptions, defaultSort())
                .flatMap(client::delete)
                .then();
    }

    private Mono<Void> deleteUserPersonalAccessTokens(String username) {
        var listOptions = ListOptions.builder()
                .andQuery(equal("spec.username", username))
                .build();
        return client.listAll(PersonalAccessToken.class, listOptions, defaultSort())
                .flatMap(client::delete)
                .then();
    }

    private Mono<Void> deleteUserGroupMemberships(String username) {
        var subject = UserGroupBinding.Subject.user(username).toString();
        var listOptions = ListOptions.builder()
                .andQuery(equal("subjects", subject))
                .build();
        return client.listAll(UserGroupBinding.class, listOptions, defaultSort())
                .filter(binding -> Optional.ofNullable(binding.getRoleRef())
                        .map(ref -> UserGroupBinding.GROUP.equals(ref.getApiGroup()))
                        .orElse(false))
                .filter(binding -> !isDeleted(binding))
                .flatMap(client::delete)
                .then();
    }
}
