package cn.tolim.pudans.core.user.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ListResult;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.PageRequestImpl;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.router.SortableRequest;
import cn.tolim.pudans.contract.extension.router.selector.SelectorUtil;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import cn.tolim.pudans.core.rbac.service.UiPermissionService;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import cn.tolim.pudans.core.user.service.FeishuUserService;
import cn.tolim.pudans.infra.exception.DuplicateNameException;

@Component
@RequiredArgsConstructor
public class UserEndpoint implements CustomEndpoint {

    private static final String SELF = "-";

    private final ReactiveExtensionClient client;

    private final UserService userService;

    private final RoleService roleService;

    private final UserGroupService userGroupService;

    private final FeishuUserService feishuUserService;

    private final AuditService auditService;

    private final BuiltinEntityPolicy builtinEntityPolicy;

    private final DomainEventPublisher domainEventPublisher;

    private final UiPermissionService uiPermissionService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "UserV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/users/-", this::me, builder -> builder.operationId("GetCurrentUser")
                        .description("Get the current authenticated user.")
                        .tag(tag)
                        .response(responseBuilder().implementation(User.class)))
                .GET("/users/{name}", this::getUserByName, builder -> builder.operationId("GetUser")
                        .description("Get a user by metadata.name.")
                        .tag(tag)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .response(responseBuilder().implementation(User.class)))
                .GET("/users", this::listUsers, builder -> {
                    builder.operationId("ListUsers")
                            .description("List users.")
                            .tag(tag)
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("keyword")
                                    .required(false)
                                    .description(
                                            "Case-insensitive fuzzy match against displayName and email. "
                                                    + "Disables database paging and filters in memory.")
                                    .implementation(String.class))
                            .response(responseBuilder().implementation(UserList.class));
                    SortableRequest.buildParameters(builder);
                })
                .POST("/users", this::createUser, builder -> builder.operationId("CreateUser")
                        .description("Create a local user backed by a Feishu identity.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder().required(true).implementation(CreateUserRequest.class))
                        .response(responseBuilder().implementation(User.class)))
                .POST("/users/-/batch", this::batchCreateUsers, builder -> builder.operationId("BatchCreateUsers")
                        .description("Batch create local users and bind their Feishu identities.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder().required(true).implementation(BatchCreateRequest.class))
                        .response(responseBuilder().implementation(BatchCreateResponse.class)))
                .PUT("/users/-", this::updateSelf, builder -> builder.operationId("UpdateCurrentUser")
                        .description("Update the current user's local profile. displayName and email are read-only.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder().required(true).implementation(UpdateProfileRequest.class))
                        .response(responseBuilder().implementation(User.class)))
                .PUT("/users/{name}", this::updateUser, builder -> builder.operationId("UpdateUser")
                        .description("Update a user's local profile. displayName and email are read-only.")
                        .tag(tag)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .requestBody(requestBodyBuilder().required(true).implementation(UpdateProfileRequest.class))
                        .response(responseBuilder().implementation(User.class)))
                .DELETE("/users/{name}", this::deleteUser, builder -> builder.operationId("DeleteUser")
                        .description("Delete a user and all of the user's identity bindings.")
                        .tag(tag)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .response(responseBuilder().implementation(User.class)))
                .GET("/users/-/permissions", this::selfPermissions, builder -> builder.operationId("GetMyPermissions")
                        .description("Get the current user's roles and permissions.")
                        .tag(tag)
                        .response(responseBuilder().implementation(UserPermission.class)))
                .GET("/users/-/ui-permissions", this::uiPermissions, builder -> builder.operationId("GetMyUiPermissions")
                        .description("Get the aggregated ui permission tags of the current user.")
                        .tag(tag)
                        .response(responseBuilder().implementation(String.class)))
                .GET("/users/{name}/permissions", this::userPermissions, builder -> builder.operationId("GetUserPermissions")
                        .description("Get a user's roles and permissions.")
                        .tag(tag)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .response(responseBuilder().implementation(UserPermission.class)))
                .PUT("/users/-/password", this::changeOwnPassword, builder -> builder.operationId("ChangeMyPassword")
                        .description("Change the current user's local password.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder().required(true).implementation(PasswordRequest.class))
                        .response(responseBuilder().implementation(User.class)))
                .PUT("/users/{name}/password", this::changePasswordForUser, builder -> builder.operationId("ChangeUserPassword")
                        .description("Change another user's local password (admin).")
                        .tag(tag)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .requestBody(requestBodyBuilder().required(true).implementation(PasswordRequest.class))
                        .response(responseBuilder().implementation(User.class)))
                .GET("/users/{name}/usergroup", this::getUserGroup, builder -> builder.operationId("GetUserGroup")
                        .description("Get the user group of a user.")
                        .tag(tag)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .response(responseBuilder().implementation(UserGroup.class)))
                .POST("/users/{name}/usergroup", this::assignUserGroup, builder -> builder.operationId("AssignUserGroup")
                        .description("Assign the user to exactly one user group, replacing any previous group.")
                        .tag(tag)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .requestBody(requestBodyBuilder().required(true).implementation(AssignGroupRequest.class))
                        .response(responseBuilder().implementation(User.class)))
                .DELETE("/users/{name}/usergroup", this::removeUserGroup, builder -> builder.operationId("RemoveUserGroup")
                        .description("Remove the user from its user group.")
                        .tag(tag)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .response(responseBuilder().implementation(User.class)))
                .build();
    }

    private Mono<String> currentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .map(org.springframework.security.core.Authentication::getName)
                .switchIfEmpty(Mono.error(new ServerWebInputException("Not authenticated")));
    }

    private Mono<String> resolveName(ServerRequest request) {
        var name = request.pathVariable("name");
        return SELF.equals(name) ? currentUsername() : Mono.just(name);
    }

    private Mono<ServerResponse> me(ServerRequest request) {
        return currentUsername()
                .flatMap(userService::getUser)
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    private Mono<ServerResponse> getUserByName(ServerRequest request) {
        return resolveName(request)
                .flatMap(userService::getUser)
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    private Mono<ServerResponse> listUsers(ServerRequest request) {
        var sortable = new SortableRequest(request.exchange());
        var listOptions = sortable.toListOptions();
        var keyword = request.queryParam("keyword").filter(StringUtils::hasText).map(String::trim).orElse(null);
        var pageRequest = sortable.toPageRequest();
        if (keyword == null) {
            return client.listBy(User.class, listOptions, pageRequest)
                    .map(result -> new UserList(
                            result.getPage(),
                            result.getSize(),
                            result.getTotal(),
                            result.getItems().stream().map(ListedUser::from).toList(),
                            sortable.getSort().toString()))
                    .flatMap(list -> ServerResponse.ok().bodyValue(list));
        }
        return client
                .listAll(User.class, listOptions, sortable.getSort())
                .filter(user -> matchesKeyword(user, keyword))
                .collectList()
                .map(users -> pagedUserList(users, sortable))
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    private static boolean matchesKeyword(User user, String keyword) {
        var needle = keyword.toLowerCase(java.util.Locale.ROOT);
        var spec = user.getSpec();
        var displayName = spec == null ? null : spec.getDisplayName();
        var email = spec == null ? null : spec.getEmail();
        return containsIgnoreCase(displayName, needle) || containsIgnoreCase(email, needle);
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    private UserList pagedUserList(List<User> users, SortableRequest sortable) {
        // Page numbers are 1-based, matching PageRequestImpl.
        var page = Math.max(1, sortable.getPage());
        var size = sortable.getSize();
        var total = users.size();
        var from = size > 0 ? Math.min((long) (page - 1) * size, total) : 0L;
        var to = size > 0 ? Math.min(from + size, total) : (long) total;
        var items = users.subList((int) from, (int) to).stream().map(ListedUser::from).toList();
        return new UserList(page, size, total, items, sortable.getSort().toString());
    }

    private Mono<ServerResponse> createUser(ServerRequest request) {
        return request.bodyToMono(CreateUserRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("Request body is required.")))
                .flatMap(body -> feishuUserService.createFromFeishu(body.toFeishuData(), body.userGroup()))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    private Mono<ServerResponse> batchCreateUsers(ServerRequest request) {
        return request.bodyToMono(BatchCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("Request body is required.")))
                .flatMap(body -> {
                    if (CollectionUtils.isEmpty(body.users())) {
                        return Mono.error(new ServerWebInputException("users must not be empty."));
                    }
                    return Flux.fromIterable(body.users())
                            .concatMap(item -> feishuUserService
                                    .createFromFeishu(item.toFeishuData(), item.userGroup())
                                    .flatMap(user -> auditService
                                            .recordChild(
                                                    request.exchange(),
                                                    null,
                                                    "BATCH_CREATE_USER",
                                                    "User",
                                                    user.getMetadata().getName(),
                                                    "SUCCESS",
                                                    snapshot(item, user),
                                                    null)
                                            .thenReturn(user)))
                            .collectList()
                            .map(created -> new BatchCreateResponse(
                                    created.size(),
                                    created.stream().map(UserView::from).toList()));
                })
                .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    private Map<String, Object> snapshot(FeishuUserItem item, User user) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("before", null);
        var after = new LinkedHashMap<String, Object>();
        after.put("name", user.getMetadata().getName());
        after.put("uid", user.getSpec().getUid());
        after.put("displayName", user.getSpec().getDisplayName());
        after.put("email", user.getSpec().getEmail());
        after.put("unionId", item.unionId());
        after.put("openId", item.openId());
        after.put("userGroup", item.userGroup());
        snapshot.put("after", after);
        return snapshot;
    }

    private Mono<ServerResponse> updateSelf(ServerRequest request) {
        return resolveName(request)
                .flatMap(username -> request.bodyToMono(UpdateProfileRequest.class)
                        .switchIfEmpty(Mono.error(new ServerWebInputException("Request body is required.")))
                        .flatMap(update -> applyProfileUpdate(request.exchange(), username, update)))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    private Mono<ServerResponse> updateUser(ServerRequest request) {
        return resolveName(request)
                .flatMap(username -> request.bodyToMono(UpdateProfileRequest.class)
                        .switchIfEmpty(Mono.error(new ServerWebInputException("Request body is required.")))
                        .flatMap(update -> applyProfileUpdate(request.exchange(), username, update)))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    private Mono<User> applyProfileUpdate(
            org.springframework.web.server.ServerWebExchange exchange, String username, UpdateProfileRequest update) {
        return userService.getUser(username).flatMap(user -> {
            var spec = user.getSpec();
            if (update.displayName() != null && !Objects.equals(update.displayName(), spec.getDisplayName())) {
                return Mono.error(new ServerWebInputException(
                        "displayName is read-only: user data must come from Feishu."));
            }
            if (update.email() != null && !Objects.equals(update.email(), spec.getEmail())) {
                return Mono.error(
                        new ServerWebInputException("email is read-only: user data must come from Feishu."));
            }
            var changedFields = new ArrayList<String>();
            if (update.avatar() != null) {
                spec.setAvatar(update.avatar());
                changedFields.add("avatar");
            }
            if (update.phone() != null) {
                spec.setPhone(update.phone());
                changedFields.add("phone");
            }
            if (update.bio() != null) {
                spec.setBio(update.bio());
                changedFields.add("bio");
            }
            return client.update(user).doOnNext(updated -> {
                if (!changedFields.isEmpty()) {
                    publishUpdated(exchange, updated, changedFields);
                }
            });
        });
    }

    private void publishUpdated(
            org.springframework.web.server.ServerWebExchange exchange, User user, List<String> changedFields) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", user.getMetadata().getName());
        payload.put("uid", user.getSpec().getUid());
        payload.put("displayName", user.getSpec().getDisplayName());
        payload.put("email", user.getSpec().getEmail());
        payload.put("changedFields", changedFields);
        payload.put("source", "local");
        domainEventPublisher.publish(
                "user.updated",
                payload,
                (String) exchange.getAttributes().get(AuditService.TRACE_FLOW_ID_ATTRIBUTE));
    }

    private Mono<ServerResponse> deleteUser(ServerRequest request) {
        return resolveName(request)
                .flatMap(name -> client.get(User.class, name))
                .flatMap(user -> builtinEntityPolicy.checkUserDeletion(user).thenReturn(user))
                .flatMap(client::delete)
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    private Mono<ServerResponse> selfPermissions(ServerRequest request) {
        return currentUsername().flatMap(this::buildPermission);
    }

    private Mono<ServerResponse> uiPermissions(ServerRequest request) {
        return currentUsername()
                .flatMap(uiPermissionService::uiPermissionsOf)
                .flatMap(permissions -> ServerResponse.ok().bodyValue(permissions));
    }

    private Mono<ServerResponse> userPermissions(ServerRequest request) {
        return resolveName(request).flatMap(this::buildPermission);
    }

    private Mono<ServerResponse> buildPermission(String username) {
        return userGroupService
                .getRoleNamesByUsername(username)
                .collectList()
                .flatMap(roleNames -> {
                    var assigned = Set.copyOf(roleNames);
                    return roleService
                            .listPermissions(assigned)
                            .map(role -> role.getMetadata().getName())
                            .collect(Collectors.toSet())
                            .map(permissions ->
                                    new UserPermission(username, assigned.stream().toList(), permissions));
                })
                .flatMap(permission -> ServerResponse.ok().bodyValue(permission));
    }

    private Mono<ServerResponse> changeOwnPassword(ServerRequest request) {
        return resolveName(request)
                .flatMap(username -> request.bodyToMono(PasswordRequest.class)
                        .flatMap(password -> userService.updateWithRawPassword(username, password.password())))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    private Mono<ServerResponse> changePasswordForUser(ServerRequest request) {
        return resolveName(request)
                .flatMap(username -> request.bodyToMono(PasswordRequest.class)
                        .flatMap(password -> userService.updateWithRawPassword(username, password.password())))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    private Mono<ServerResponse> getUserGroup(ServerRequest request) {
        return resolveName(request)
                .flatMap(userGroupService::getUserGroupByUsername)
                .flatMap(group -> ServerResponse.ok().bodyValue(group))
                .switchIfEmpty(Mono.error(new ServerWebInputException("The user has no user group.")));
    }

    private Mono<ServerResponse> assignUserGroup(ServerRequest request) {
        return resolveName(request)
                .flatMap(username -> request.bodyToMono(AssignGroupRequest.class)
                        .flatMap(body -> userGroupService
                                .assignUserGroup(username, body.userGroup())
                                .then(userService.getUser(username))))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    private Mono<ServerResponse> removeUserGroup(ServerRequest request) {
        return resolveName(request)
                .flatMap(username -> userGroupService.removeUserGroup(username).then(userService.getUser(username)))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.CONSOLE + "/v1alpha1");
    }

    
    public record CreateUserRequest(
            String username,
            @Schema(description = "Feishu nickname. Read-only after creation.") String displayName,
            @Schema(description = "Feishu email. Read-only after creation.") String email,
            String openId,
            String unionId,
            String avatar,
            String userGroup) {
        FeishuUserService.FeishuUserData toFeishuData() {
            return new FeishuUserService.FeishuUserData(username, displayName, email, openId, unionId, avatar);
        }
    }

    
    public record BatchCreateRequest(List<FeishuUserItem> users) {}

    
    public record FeishuUserItem(
            String username,
            @Schema(description = "Feishu nickname. Read-only after creation.") String displayName,
            @Schema(description = "Feishu email. Read-only after creation.") String email,
            String openId,
            String unionId,
            String avatar,
            String userGroup) {
        FeishuUserService.FeishuUserData toFeishuData() {
            return new FeishuUserService.FeishuUserData(username, displayName, email, openId, unionId, avatar);
        }
    }

    
    public record BatchCreateResponse(int created, List<UserView> users) {}

    
    public record UpdateProfileRequest(String displayName, String email, String avatar, String phone, String bio) {}

    
    public record PasswordRequest(String password) {}

    
    public record AssignGroupRequest(String userGroup) {}

    
    public record UserPermission(String username, List<String> roles, Set<String> permissions) {}

    
    public record ListedUser(String name, String displayName, String email, Instant registeredAt, boolean disabled) {
        static ListedUser from(User user) {
            return new ListedUser(
                    user.getMetadata().getName(),
                    user.getSpec().getDisplayName(),
                    user.getSpec().getEmail(),
                    user.getSpec().getRegisteredAt(),
                    Boolean.TRUE.equals(user.getSpec().getDisabled()));
        }
    }

    
    public record UserList(int page, int size, long total, List<ListedUser> items, String sort) {}
}
