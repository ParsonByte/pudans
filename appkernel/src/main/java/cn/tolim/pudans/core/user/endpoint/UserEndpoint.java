package cn.tolim.pudans.core.user.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.extension.user.UserConnection;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.contract.extension.PageRequestImpl;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.router.SortableRequest;
import cn.tolim.pudans.contract.infra.ValidationUtils;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import cn.tolim.pudans.core.rbac.service.UiPermissionService;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import cn.tolim.pudans.core.user.service.FeishuUserService;
import cn.tolim.pudans.core.user.service.LocalUserService;
import cn.tolim.pudans.core.user.service.UserConnectionService;
import cn.tolim.pudans.infra.exception.UnsatisfiedAttributeValueException;
import cn.tolim.pudans.infra.setting.ParameterSettingGroupService;

@Component
@RequiredArgsConstructor
public class UserEndpoint implements CustomEndpoint {

    private static final String SELF = "-";

    private static final String MANAGE_USERS_CAPABILITY = "manage-users";

    private static final int MAX_LOCAL_BATCH_SIZE = 200;

    private final ReactiveExtensionClient client;

    private final UserService userService;

    private final RoleService roleService;

    private final UserGroupService userGroupService;

    private final FeishuUserService feishuUserService;

    private final AuditService auditService;

    private final BuiltinEntityPolicy builtinEntityPolicy;

    private final DomainEventPublisher domainEventPublisher;

    private final UiPermissionService uiPermissionService;

    private final ParameterSettingGroupService parameterSettingGroupService;

    private final LocalUserService localUserService;

    private final UserConnectionService userConnectionService;

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
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("status")
                                    .required(false)
                                    .description("Filter by account status: active or disabled.")
                                    .implementation(String.class))
                            .parameter(parameterBuilder()
                                    .in(ParameterIn.QUERY)
                                    .name("userGroup")
                                    .required(false)
                                    .description("Filter by user group name. Disables database paging and filters in memory.")
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
                .POST("/users/-/local", this::createLocalUser, builder -> builder.operationId("CreateLocalUser")
                        .description("Create a local user with a username and password.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder().required(true).implementation(LocalCreateUserRequest.class))
                        .response(responseBuilder().implementation(UserView.class)))
                .POST("/users/-/local/batch", this::batchCreateLocalUsers, builder -> builder
                        .operationId("BatchCreateLocalUsers")
                        .description("Batch create local users with usernames and passwords.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder().required(true).implementation(LocalBatchCreateRequest.class))
                        .response(responseBuilder().implementation(LocalBatchCreateResponse.class)))
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
                .GET("/users/{name}/connections", this::listUserConnections, builder -> builder
                        .operationId("ListUserConnections")
                        .description("List a user's external identity connections.")
                        .tag(tag)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .response(responseBuilder().implementationArray(UserConnectionView.class)))
                .GET("/users/{name}/activity", this::userActivity, builder -> builder
                        .operationId("GetUserActivity")
                        .description("Daily audit activity counts for a user.")
                        .tag(tag)
                        .parameter(parameterBuilder().in(ParameterIn.PATH).name("name").required(true))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("days")
                                .required(false)
                                .description("Number of UTC days to return (1-90, default 14).")
                                .implementation(Integer.class))
                        .response(responseBuilder().implementationArray(UserActivityPoint.class)))
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
                .switchIfEmpty(Mono.error(new ServerWebInputException("未认证")));
    }

    private Mono<String> resolveName(ServerRequest request) {
        var name = request.pathVariable("name");
        return SELF.equals(name) ? currentUsername() : Mono.just(name);
    }

    private Mono<ServerResponse> me(ServerRequest request) {
        return currentUsername()
                .flatMap(userService::getUser)
                .flatMap(user -> ServerResponse.ok()
                        .bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user, true)));
    }

    private Mono<ServerResponse> getUserByName(ServerRequest request) {
        return resolveName(request)
                .flatMap(name -> emailVisibilityFor(name)
                        .flatMap(expose -> userService
                                .getUser(name)
                                .map(user -> cn.tolim.pudans.core.user.endpoint.UserView.from(user, expose))))
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private Mono<Boolean> emailVisibility() {
        return parameterSettingGroupService
                .getSession()
                .map(session -> Boolean.TRUE.equals(session.getExposeEmailToSignedInUsers()))
                .defaultIfEmpty(false);
    }

    private Mono<Boolean> emailVisibilityFor(String name) {
        return Mono.zip(
                        emailVisibility(),
                        canManageUsers(),
                        currentUsername().defaultIfEmpty(""))
                .map(tuple -> tuple.getT1() || tuple.getT2() || tuple.getT3().equals(name));
    }

    private Mono<Boolean> emailVisibilityForList() {
        return Mono.zip(emailVisibility(), canManageUsers())
                .map(tuple -> tuple.getT1() || tuple.getT2());
    }

    private Mono<Boolean> canManageUsers() {
        return currentUsername()
                .flatMap(username -> uiPermissionService.hasCapability(username, MANAGE_USERS_CAPABILITY))
                .defaultIfEmpty(false);
    }

    private Mono<ServerResponse> listUsers(ServerRequest request) {
        var sortable = new SortableRequest(request.exchange());
        var listOptions = sortable.toListOptions();
        var keyword = request.queryParam("keyword").filter(StringUtils::hasText).map(String::trim).orElse(null);
        var statuses = statusValues(request);
        var userGroups = userGroupValues(request);
        var pageRequest = sortable.toPageRequest();
        return emailVisibilityForList().flatMap(expose -> {
            if (keyword == null && statuses.isEmpty() && userGroups.isEmpty()) {
                return client.listBy(User.class, listOptions, pageRequest)
                        .flatMap(result -> groupsOf(result.getItems())
                                .map(groupsByUser -> new UserList(
                                        result.getPage(),
                                        result.getSize(),
                                        result.getTotal(),
                                        result.getItems().stream()
                                                .map(user -> ListedUser.from(
                                                        user,
                                                        expose,
                                                        groupsByUser.get(user.getMetadata().getName())))
                                                .toList(),
                                        sortable.getSort().toString())))
                        .flatMap(list -> ServerResponse.ok().bodyValue(list));
            }
            return client
                    .listAll(User.class, listOptions, sortable.getSort())
                    .filter(user -> matchesKeyword(user, keyword) && matchesStatus(user, statuses))
                    .collectList()
                    .flatMap(users -> groupsOf(users)
                            .map(groupsByUser -> {
                                var filtered = userGroups.isEmpty()
                                        ? users
                                        : users.stream()
                                                .filter(user -> {
                                                    var group = groupsByUser.get(
                                                            user.getMetadata().getName());
                                                    return group != null
                                                            && userGroups.contains(
                                                                    group.getMetadata().getName());
                                                })
                                                .toList();
                                return pagedUserList(filtered, groupsByUser, sortable, expose);
                            }))
                    .flatMap(list -> ServerResponse.ok().bodyValue(list));
        });
    }

    private static Set<String> statusValues(ServerRequest request) {
        return request.queryParams().getOrDefault("status", List.of()).stream()
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static Set<String> userGroupValues(ServerRequest request) {
        return request.queryParams().getOrDefault("userGroup", List.of()).stream()
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private Mono<Map<String, UserGroup>> groupsOf(List<User> users) {
        return Flux.fromIterable(users)
                .flatMap(user -> userGroupService
                        .getUserGroupByUsername(user.getMetadata().getName())
                        .map(group -> Map.entry(user.getMetadata().getName(), group)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private static boolean matchesKeyword(User user, String keyword) {
        if (keyword == null) {
            return true;
        }
        var needle = keyword.toLowerCase(java.util.Locale.ROOT);
        var spec = user.getSpec();
        var displayName = spec == null ? null : spec.getDisplayName();
        var email = spec == null ? null : spec.getEmail();
        return containsIgnoreCase(displayName, needle) || containsIgnoreCase(email, needle);
    }

    private static boolean matchesStatus(User user, Set<String> statuses) {
        if (statuses.isEmpty()) {
            return true;
        }
        var disabled = Boolean.TRUE.equals(user.getSpec().getDisabled());
        return statuses.contains(disabled ? "disabled" : "active");
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    private UserList pagedUserList(
            List<User> users,
            Map<String, UserGroup> groupsByUser,
            SortableRequest sortable,
            boolean exposeEmail) {
        // 页码从 1 开始，与 PageRequestImpl 保持一致。
        var page = Math.max(1, sortable.getPage());
        var size = sortable.getSize();
        var total = users.size();
        var from = size > 0 ? Math.min((long) (page - 1) * size, total) : 0L;
        var to = size > 0 ? Math.min(from + size, total) : (long) total;
        var items = users.subList((int) from, (int) to).stream()
                .map(user -> ListedUser.from(
                        user, exposeEmail, groupsByUser.get(user.getMetadata().getName())))
                .toList();
        return new UserList(page, size, total, items, sortable.getSort().toString());
    }

    private Mono<ServerResponse> createUser(ServerRequest request) {
        return request.bodyToMono(CreateUserRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                .flatMap(body -> feishuUserService.createFromFeishu(body.toFeishuData(), body.userGroup()))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    private Mono<ServerResponse> batchCreateUsers(ServerRequest request) {
        return request.bodyToMono(BatchCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                .flatMap(body -> {
                    if (CollectionUtils.isEmpty(body.users())) {
                        return Mono.error(new ServerWebInputException("users 不能为空。"));
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

    private Mono<ServerResponse> createLocalUser(ServerRequest request) {
        return request
                .bodyToMono(LocalCreateUserRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                .flatMap(body -> {
                    validateLocalCreate(body);
                    return localUserService
                            .create(
                                    body.username().trim(),
                                    body.displayName(),
                                    body.email(),
                                    body.password(),
                                    body.avatar(),
                                    body.userGroup())
                            .map(user -> new CreatedLocalUser(body, user));
                })
                .flatMap(created -> auditService
                        .recordChild(
                                request.exchange(),
                                null,
                                "USER_CREATE",
                                "User",
                                created.user().getMetadata().getName(),
                                "SUCCESS",
                                localUserSnapshot(created.request(), created.user()),
                                null)
                        .thenReturn(created.user()))
                .flatMap(user -> ServerResponse.ok().bodyValue(UserView.from(user, true)));
    }

    private Mono<ServerResponse> batchCreateLocalUsers(ServerRequest request) {
        return request
                .bodyToMono(LocalBatchCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                .flatMap(body -> {
                    validateLocalBatch(body);
                    return Flux.fromIterable(body.users())
                            .index()
                            .concatMap(item -> {
                                var index = item.getT1().intValue() + 1;
                                var bodyItem = item.getT2();
                                return createLocalUserForBatch(request, bodyItem)
                                        .then(Mono.<LocalBatchCreateFailure>empty())
                                        .onErrorResume(error -> Mono.just(new LocalBatchCreateFailure(
                                                index,
                                                bodyItem == null ? null : bodyItem.username(),
                                                localCreateFailureMessage(error))));
                            })
                            .collectList()
                            .map(failures -> new LocalBatchCreateResponse(
                                    body.users().size() - failures.size(), failures.size(), failures));
                })
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<User> createLocalUserForBatch(ServerRequest request, LocalCreateUserRequest body) {
        return Mono.defer(() -> {
            validateLocalCreate(body);
            return localUserService
                    .create(
                            body.username().trim(),
                            body.displayName(),
                            body.email(),
                            body.password(),
                            body.avatar(),
                            body.userGroup())
                    .flatMap(user -> auditService
                            .recordChild(
                                    request.exchange(),
                                    null,
                                    "BATCH_CREATE_USER",
                                    "User",
                                    user.getMetadata().getName(),
                                    "SUCCESS",
                                    localUserSnapshot(body, user),
                                    null)
                            .thenReturn(user));
        });
    }

    static void validateLocalCreate(LocalCreateUserRequest body) {
        var username = StringUtils.hasText(body.username()) ? body.username().trim() : null;
        if (username == null || username.length() > 63 || !username.matches(ValidationUtils.NAME_REGEX)) {
            throw new ServerWebInputException(
                    "用户名只能包含小写字母、数字、- 和 .，且以字母或数字开头和结尾，最长 63 个字符。");
        }
        if (StringUtils.hasText(body.displayName()) && body.displayName().trim().length() > 80) {
            throw new ServerWebInputException("显示名称不能超过 80 个字符。");
        }
        if (StringUtils.hasText(body.email())
                && (body.email().trim().length() > 254
                        || !ValidationUtils.EMAIL_PATTERN.matcher(body.email().trim()).matches())) {
            throw new ServerWebInputException("请输入有效的邮箱地址。");
        }
        if (!ValidationUtils.isPasswordLengthValid(body.password())
                || !ValidationUtils.PASSWORD_PATTERN.matcher(body.password()).matches()) {
            throw new ServerWebInputException(
                    "密码长度需为 8-257 位，且只能包含字母、数字和 !@#$%^&*.?。");
        }
        if (body.avatar() != null && body.avatar().length() > 2048) {
            throw new ServerWebInputException("头像地址过长。");
        }
        if (StringUtils.hasText(body.userGroup()) && body.userGroup().length() > 63) {
            throw new ServerWebInputException("用户组名称过长。");
        }
    }

    static void validateLocalBatch(LocalBatchCreateRequest body) {
        if (body == null || CollectionUtils.isEmpty(body.users())) {
            throw new ServerWebInputException("users 不能为空。");
        }
        if (body.users().size() > MAX_LOCAL_BATCH_SIZE) {
            throw new ServerWebInputException("单次最多创建 " + MAX_LOCAL_BATCH_SIZE + " 个用户。");
        }
        if (body.users().stream().anyMatch(Objects::isNull)) {
            throw new ServerWebInputException("users 不能包含空项。");
        }
    }

    private static String localCreateFailureMessage(Throwable error) {
        if (error instanceof ResponseStatusException status && StringUtils.hasText(status.getReason())) {
            return status.getReason();
        }
        return "创建失败，请稍后重试。";
    }

    private Map<String, Object> localUserSnapshot(LocalCreateUserRequest request, User user) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("before", null);
        var after = new LinkedHashMap<String, Object>();
        after.put("name", user.getMetadata().getName());
        after.put("uid", user.getSpec().getUid());
        after.put("displayName", user.getSpec().getDisplayName());
        after.put("email", user.getSpec().getEmail());
        after.put("userGroup", request.userGroup());
        snapshot.put("after", after);
        return snapshot;
    }

    private record CreatedLocalUser(LocalCreateUserRequest request, User user) {}

    private Mono<ServerResponse> updateSelf(ServerRequest request) {
        return resolveName(request)
                .flatMap(username -> request.bodyToMono(UpdateProfileRequest.class)
                        .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                        .flatMap(update -> applyProfileUpdate(request.exchange(), username, update)))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    private Mono<ServerResponse> updateUser(ServerRequest request) {
        return resolveName(request)
                .flatMap(username -> request.bodyToMono(UpdateProfileRequest.class)
                        .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                        .flatMap(update -> applyProfileUpdate(request.exchange(), username, update)))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    private Mono<User> applyProfileUpdate(
            org.springframework.web.server.ServerWebExchange exchange, String username, UpdateProfileRequest update) {
        return userService.getUser(username).flatMap(user -> {
            var spec = user.getSpec();
            if (update.displayName() != null && !Objects.equals(update.displayName(), spec.getDisplayName())) {
                return Mono.error(new ServerWebInputException(
                        "displayName 为只读字段：用户数据必须来自飞书。"));
            }
            if (update.email() != null && !Objects.equals(update.email(), spec.getEmail())) {
                return Mono.error(
                        new ServerWebInputException("email 为只读字段：用户数据必须来自飞书。"));
            }
            if (update.avatar() != null && !Objects.equals(update.avatar(), spec.getAvatar())) {
                return Mono.error(
                        new ServerWebInputException("avatar 为只读字段：用户数据必须来自飞书。"));
            }
            if (update.phone() != null && !Objects.equals(update.phone(), spec.getPhone())) {
                return Mono.error(
                        new ServerWebInputException("phone 为只读字段：用户数据必须来自飞书。"));
            }
            var changedFields = new ArrayList<String>();
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
        return changePassword(request, true);
    }

    private Mono<ServerResponse> changePasswordForUser(ServerRequest request) {
        return changePassword(request, false);
    }

    private Mono<ServerResponse> changePassword(ServerRequest request, boolean verifyOldPassword) {
        return resolveName(request)
                .flatMap(username -> request.bodyToMono(PasswordRequest.class)
                        .flatMap(password -> verifyOldPassword
                                ? userService
                                        .confirmPassword(username, password.oldPassword())
                                        .flatMap(confirmed -> Boolean.TRUE.equals(confirmed)
                                                ? userService.updateWithRawPassword(username, password.password())
                                                : Mono.error(new UnsatisfiedAttributeValueException("旧密码不匹配。")))
                                : userService.updateWithRawPassword(username, password.password())))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    private Mono<ServerResponse> getUserGroup(ServerRequest request) {
        return resolveName(request)
                .flatMap(userGroupService::getUserGroupByUsername)
                .flatMap(group -> ServerResponse.ok().bodyValue(group))
                .switchIfEmpty(Mono.error(new ServerWebInputException("用户未加入任何用户组。")));
    }

    private Mono<ServerResponse> listUserConnections(ServerRequest request) {
        return resolveName(request)
                .flatMap(userService::getUser)
                .flatMapMany(user -> userConnectionService.listConnections(user.getMetadata().getName()))
                .map(UserConnectionView::from)
                .collectList()
                .flatMap(connections -> ServerResponse.ok().bodyValue(connections));
    }

    private Mono<ServerResponse> userActivity(ServerRequest request) {
        var days = request.queryParam("days")
                .filter(StringUtils::hasText)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return 14;
                    }
                })
                .orElse(14);
        days = Math.max(1, Math.min(90, days));
        var activityDays = days;
        return resolveName(request)
                .flatMap(userService::getUser)
                .flatMap(user -> {
                    var end = LocalDate.now(ZoneOffset.UTC);
                    var start = end.minusDays(activityDays - 1L);
                    var uid = user.getSpec().getUid();
                    var counts = StringUtils.hasText(uid)
                            ? auditService.countPerDay(
                                    start.atStartOfDay(ZoneOffset.UTC).toInstant(), uid, null, null)
                            : Mono.just(Map.<LocalDate, Long>of());
                    return counts.map(values -> IntStream.range(0, activityDays)
                            .mapToObj(offset -> {
                                var date = start.plusDays(offset);
                                return new UserActivityPoint(date, values.getOrDefault(date, 0L));
                            })
                            .toList());
                })
                .flatMap(points -> ServerResponse.ok().bodyValue(points));
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
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
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

    public record LocalCreateUserRequest(
            String username,
            String displayName,
            String email,
            String password,
            String avatar,
            String userGroup) {}


    public record LocalBatchCreateRequest(List<LocalCreateUserRequest> users) {}


    public record LocalBatchCreateFailure(int index, String username, String message) {}


    public record LocalBatchCreateResponse(
            int created, int failed, List<LocalBatchCreateFailure> failures) {}

    
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

    
    public record PasswordRequest(
            @Size(min = 8, max = 257) String password, @Size(max = 257) String oldPassword) {}

    
    public record AssignGroupRequest(String userGroup) {}

    
    public record UserPermission(String username, List<String> roles, Set<String> permissions) {}


    public record UserConnectionView(
            String name,
            String registrationId,
            String providerUserId,
            String displayName,
            Instant updatedAt,
            Instant createdAt) {
        static UserConnectionView from(UserConnection connection) {
            return new UserConnectionView(
                    connection.getMetadata().getName(),
                    connection.getSpec().getRegistrationId(),
                    connection.getSpec().getProviderUserId(),
                    connection.getSpec().getDisplayName(),
                    connection.getSpec().getUpdatedAt(),
                    connection.getMetadata().getCreationTimestamp());
        }
    }


    public record UserActivityPoint(LocalDate date, long count) {}

    
    public record ListedUser(
            String name,
            String displayName,
            String email,
            String phone,
            Instant registeredAt,
            boolean disabled,
            String userGroup,
            String userGroupDisplayName) {
        static ListedUser from(User user) {
            return from(user, true, null);
        }

        static ListedUser from(User user, boolean exposeEmail) {
            return from(user, exposeEmail, null);
        }

        static ListedUser from(User user, boolean exposeEmail, UserGroup group) {
            return new ListedUser(
                    user.getMetadata().getName(),
                    user.getSpec().getDisplayName(),
                    exposeEmail ? user.getSpec().getEmail() : null,
                    user.getSpec().getPhone(),
                    user.getSpec().getRegisteredAt(),
                    Boolean.TRUE.equals(user.getSpec().getDisabled()),
                    group == null ? null : group.getMetadata().getName(),
                    group == null ? null : group.getDisplayName());
        }
    }

    
    public record UserList(int page, int size, long total, List<ListedUser> items, String sort) {}
}
