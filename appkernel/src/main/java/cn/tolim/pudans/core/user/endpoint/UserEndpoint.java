package cn.tolim.pudans.core.user.endpoint;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.notDeleting;
import static cn.tolim.pudans.extension.contract.index.query.Queries.contains;
import static cn.tolim.pudans.extension.contract.index.query.Queries.equal;
import static cn.tolim.pudans.extension.contract.index.query.Queries.in;
import static cn.tolim.pudans.extension.contract.index.query.Queries.or;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.core.rbac.UserRoles;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.router.SortableRequest;
import cn.tolim.pudans.infra.contract.ValidationUtils;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import cn.tolim.pudans.core.rbac.permission.UiPermissions;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.core.user.FeishuAccounts;
import cn.tolim.pudans.core.user.LocalAccounts;
import cn.tolim.pudans.core.user.Connections;
import cn.tolim.pudans.infra.exception.UnsatisfiedAttributeValueException;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import cn.tolim.pudans.infra.utils.PudansTime;

@Component
@RequiredArgsConstructor
public class UserEndpoint implements CustomEndpoint {

    private static final String SELF = "-";

    private static final String MANAGE_USERS_CAPABILITY = "manage-users";

    private static final int MAX_BATCH_SIZE = 200;

    private final ReactiveExtensionClient client;

    private final Accounts accounts;

    private final Roles roleService;

    private final Groups groups;

    private final UserRoles userRoles;

    private final FeishuAccounts feishuAccounts;

    private final AuditLog auditLog;

    private final BuiltinEntityPolicy builtinEntityPolicy;

    private final DomainEventPublisher domainEventPublisher;

    private final UiPermissions uiPermissionService;

    private final ParameterGroups parameterGroups;

    private final LocalAccounts localAccounts;

    private final Connections userConnectionService;

    // 注册用户管理的全部接口路由
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
                                    .description("Case-insensitive fuzzy match against displayName and email.")
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
                                    .description("Filter by membership of a user group name.")
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
                .GET("/users/-/feishu-contacts", this::listFeishuContacts, builder -> builder
                        .operationId("ListFeishuContacts")
                        .description("List Feishu contacts from the organization directory.")
                        .tag(tag)
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("pageToken")
                                .required(false)
                                .description("Opaque token for the next contact page.")
                                .implementation(String.class))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("pageSize")
                                .required(false)
                                .description("Contacts per page (1-50, default 50).")
                                .implementation(Integer.class))
                        .response(responseBuilder().implementation(FeishuContactPage.class)))
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
                        .description("Update the current user's local profile. Feishu-managed fields are read-only.")
                        .tag(tag)
                        .requestBody(requestBodyBuilder().required(true).implementation(UpdateProfileRequest.class))
                        .response(responseBuilder().implementation(User.class)))
                .PUT("/users/{name}", this::updateUser, builder -> builder.operationId("UpdateUser")
                        .description("Update a user's local profile. Feishu-managed fields are read-only.")
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

    // 从安全上下文取用户名，未认证时报错
    private Mono<String> currentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .map(org.springframework.security.core.Authentication::getName)
                .switchIfEmpty(Mono.error(new ServerWebInputException("未认证")));
    }

    // 将短横线占位符解析为当前用户名
    private Mono<String> resolveName(ServerRequest request) {
        var name = request.pathVariables().get("name");
        return name == null || SELF.equals(name) ? currentUsername() : Mono.just(name);
    }

    // 返回当前登录用户的资料视图
    private Mono<ServerResponse> me(ServerRequest request) {
        return currentUsername()
                .flatMap(accounts::getUser)
                .flatMap(user -> ServerResponse.ok()
                        .bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user, true)));
    }

    // 按可见性决定是否返回邮箱
    private Mono<ServerResponse> getUserByName(ServerRequest request) {
        return resolveName(request)
                .flatMap(name -> emailVisibilityFor(name)
                        .flatMap(expose -> accounts
                                .getUser(name)
                                .map(user -> cn.tolim.pudans.core.user.endpoint.UserView.from(user, expose))))
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    // 读取配置是否允许向登录用户展示邮箱
    private Mono<Boolean> emailVisibility() {
        return parameterGroups
                .getSession()
                .map(session -> Boolean.TRUE.equals(session.getExposeEmailToSignedInUsers()))
                .defaultIfEmpty(false);
    }

    // 本人或管理员始终可见邮箱
    private Mono<Boolean> emailVisibilityFor(String name) {
        return Mono.zip(
                        emailVisibility(),
                        canManageUsers(),
                        currentUsername().defaultIfEmpty(""))
                .map(tuple -> tuple.getT1() || tuple.getT2() || tuple.getT3().equals(name));
    }

    // 列表在配置允许或管理员时展示邮箱
    private Mono<Boolean> emailVisibilityForList() {
        return Mono.zip(emailVisibility(), canManageUsers())
                .map(tuple -> tuple.getT1() || tuple.getT2());
    }

    // 判断当前用户是否具备管理能力
    private Mono<Boolean> canManageUsers() {
        return currentUsername()
                .flatMap(username -> uiPermissionService.hasCapability(username, MANAGE_USERS_CAPABILITY))
                .defaultIfEmpty(false);
    }

    // 按关键字、状态和用户组过滤并分页
    private Mono<ServerResponse> listUsers(ServerRequest request) {
        var sortable = new SortableRequest(request.exchange());
        var keyword = request.queryParam("keyword").filter(StringUtils::hasText).map(String::trim).orElse(null);
        var statuses = statusValues(request);
        var userGroups = userGroupValues(request);
        return emailVisibilityForList()
                .flatMap(expose -> listOptionsOf(sortable.toListOptions(), keyword, statuses, userGroups)
                        .flatMap(options -> client.listBy(User.class, options, sortable.toPageRequest())
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
                                                sortable.getSort().toString()))))
                        .switchIfEmpty(Mono.fromSupplier(() -> emptyUserList(sortable))))
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    // 组合状态、关键字与用户组过滤条件
    private Mono<ListOptions> listOptionsOf(
            ListOptions base,
            String keyword,
            Set<String> statuses,
            Set<String> userGroups) {
        var options = ListOptions.builder(base).andQuery(notDeleting()).build();
        if (!statuses.isEmpty()) {
            var wantsActive = statuses.contains("active");
            var wantsDisabled = statuses.contains("disabled");
            if (!wantsActive && !wantsDisabled) {
                return Mono.empty();
            }
            if (wantsActive != wantsDisabled) {
                options = ListOptions.builder(options)
                        .andQuery(equal("spec.disabled", wantsDisabled))
                        .build();
            }
        }
        if (keyword != null) {
            options = ListOptions.builder(options)
                    .andQuery(or(contains("spec.displayName", keyword), contains("spec.email", keyword)))
                    .build();
        }
        if (userGroups.isEmpty()) {
            return Mono.just(options);
        }
        var filtered = options;
        return groups.listUsernamesOfGroups(userGroups)
                .collectList()
                .flatMap(usernames -> usernames.isEmpty()
                        ? Mono.empty()
                        : Mono.just(ListOptions.builder(filtered)
                                .andQuery(in("metadata.name", new HashSet<>(usernames)))
                                .build()));
    }

    // 构造带分页信息的空列表
    private static UserList emptyUserList(SortableRequest sortable) {
        return new UserList(
                Math.max(1, sortable.getPage()),
                sortable.getSize(),
                0,
                List.of(),
                sortable.getSort().toString());
    }

    // 解析逗号分隔的状态过滤值
    private static Set<String> statusValues(ServerRequest request) {
        return request.queryParams().getOrDefault("status", List.of()).stream()
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .collect(Collectors.toSet());
    }

    // 解析逗号分隔的用户组过滤值
    private static Set<String> userGroupValues(ServerRequest request) {
        return request.queryParams().getOrDefault("userGroup", List.of()).stream()
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    // 批量查询用户所属的用户组
    private Mono<Map<String, UserGroup>> groupsOf(List<User> users) {
        return groups.getGroupsByUsernames(
                users.stream().map(user -> user.getMetadata().getName()).toList());
    }

    // 创建来自飞书身份的用户
    private Mono<ServerResponse> createUser(ServerRequest request) {
        return request.bodyToMono(CreateUserRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                .flatMap(body -> {
                    validateFeishuCreate(body.providerUserId(), body.username(), body.userGroup());
                    return feishuAccounts.createFromFeishu(body.toFeishuData(), body.userGroup());
                })
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    // 逐个创建飞书用户并记录审计
    private Mono<ServerResponse> batchCreateUsers(ServerRequest request) {
        return request.bodyToMono(BatchCreateRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                .flatMap(body -> {
                    validateBatchCreate(body);
                    return Flux.fromIterable(body.users())
                            .index()
                            .concatMap(item -> {
                                var index = item.getT1().intValue() + 1;
                                var bodyItem = item.getT2();
                                return feishuAccounts
                                        .createFromFeishu(bodyItem.toFeishuData(), bodyItem.userGroup())
                                        .flatMap(user -> auditLog
                                                .recordChild(
                                                        request.exchange(),
                                                        null,
                                                        "BATCH_CREATE_USER",
                                                        "User",
                                                        user.getMetadata().getName(),
                                                        "SUCCESS",
                                                        snapshot(bodyItem, user),
                                                        null)
                                                .thenReturn(new BatchCreateItem(
                                                        cn.tolim.pudans.core.user.endpoint.UserView.from(user),
                                                        null)))
                                        .onErrorResume(error -> Mono.just(new BatchCreateItem(
                                                null,
                                                new BatchCreateFailure(
                                                        index,
                                                        bodyItem.providerUserId(),
                                                        createFailureMessage(error)))));
                            })
                            .collectList()
                            .map(items -> {
                                var users = items.stream()
                                        .map(BatchCreateItem::user)
                                        .filter(Objects::nonNull)
                                        .toList();
                                var failures = items.stream()
                                        .map(BatchCreateItem::failure)
                                        .filter(Objects::nonNull)
                                        .toList();
                                return new BatchCreateResponse(users.size(), failures.size(), failures, users);
                            });
                })
                .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    // 分页返回飞书通讯录成员
    private Mono<ServerResponse> listFeishuContacts(ServerRequest request) {
        var pageSize = request.queryParam("pageSize")
                .filter(StringUtils::hasText)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return 50;
                    }
                })
                .orElse(50);
        var pageToken = request.queryParam("pageToken").filter(StringUtils::hasText).orElse(null);
        return feishuAccounts
                .listContacts(pageToken, Math.max(1, Math.min(50, pageSize)))
                .map(page -> new FeishuContactPage(
                        page.items().stream()
                                .map(contact -> new FeishuContactView(
                                        contact.providerUserId(),
                                        contact.openId(),
                                        contact.unionId(),
                                        contact.displayName(),
                                        contact.email(),
                                        contact.phone(),
                                        contact.avatar(),
                                        contact.imported(),
                                        contact.importedUsername()))
                                .toList(),
                        page.pageToken(),
                        page.hasMore()))
                .flatMap(page -> ServerResponse.ok().bodyValue(page));
    }

    // 组装飞书用户创建的审计快照
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

    // 校验并创建本地密码用户
    private Mono<ServerResponse> createLocalUser(ServerRequest request) {
        return request
                .bodyToMono(LocalCreateUserRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                .flatMap(body -> {
                    validateLocalCreate(body);
                    return localAccounts
                            .create(
                                    body.username().trim(),
                                    body.displayName(),
                                    body.email(),
                                    body.password(),
                                    body.avatar(),
                                    body.userGroup())
                            .map(user -> new CreatedLocalUser(body, user));
                })
                .flatMap(created -> auditLog
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

    // 逐个创建本地用户并收集失败项
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
                                                createFailureMessage(error))));
                            })
                            .collectList()
                            .map(failures -> new LocalBatchCreateResponse(
                                    body.users().size() - failures.size(), failures.size(), failures));
                })
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    // 创建单个本地用户并记录审计
    private Mono<User> createLocalUserForBatch(ServerRequest request, LocalCreateUserRequest body) {
        return Mono.defer(() -> {
            validateLocalCreate(body);
            return localAccounts
                    .create(
                            body.username().trim(),
                            body.displayName(),
                            body.email(),
                            body.password(),
                            body.avatar(),
                            body.userGroup())
                    .flatMap(user -> auditLog
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

    // 校验本地用户的名称、邮箱与密码
    static void validateLocalCreate(LocalCreateUserRequest body) {
        var username = StringUtils.hasText(body.username()) ? body.username().trim() : null;
        if (username == null) {
            throw new ServerWebInputException(
                    "用户名只能包含小写字母、数字、- 和 .，且以字母或数字开头和结尾，最长 63 个字符。");
        }
        validateUsername(username);
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

    // 限制批量创建的数量与空项
    static void validateLocalBatch(LocalBatchCreateRequest body) {
        if (body == null || CollectionUtils.isEmpty(body.users())) {
            throw new ServerWebInputException("users 不能为空。");
        }
        if (body.users().size() > MAX_BATCH_SIZE) {
            throw new ServerWebInputException("单次最多创建 " + MAX_BATCH_SIZE + " 个用户。");
        }
        if (body.users().stream().anyMatch(Objects::isNull)) {
            throw new ServerWebInputException("users 不能包含空项。");
        }
    }

    // 校验飞书用户标识和可选本地资料
    static void validateFeishuCreate(String providerUserId, String username, String userGroup) {
        var providerId = StringUtils.hasText(providerUserId) ? providerUserId.trim() : null;
        if (providerId == null
                || providerId.length() > 128
                || (!providerId.startsWith("on_") && !providerId.startsWith("ou_"))) {
            throw new ServerWebInputException("请填写有效的飞书 union_id 或 open_id。");
        }
        if (StringUtils.hasText(username)) {
            validateUsername(username.trim());
        }
        if (StringUtils.hasText(userGroup) && userGroup.trim().length() > 63) {
            throw new ServerWebInputException("用户组名称过长。");
        }
    }

    // 校验登录名的长度与允许字符
    private static void validateUsername(String username) {
        if (username.length() > 63 || !username.matches(ValidationUtils.NAME_REGEX)) {
            throw new ServerWebInputException(
                    "用户名只能包含小写字母、数字、- 和 .，且以字母或数字开头和结尾，最长 63 个字符。");
        }
    }

    // 限制飞书批量创建的数量并逐项校验
    static void validateBatchCreate(BatchCreateRequest body) {
        if (body == null || CollectionUtils.isEmpty(body.users())) {
            throw new ServerWebInputException("users 不能为空。");
        }
        if (body.users().size() > MAX_BATCH_SIZE) {
            throw new ServerWebInputException("单次最多创建 " + MAX_BATCH_SIZE + " 个用户。");
        }
        if (body.users().stream().anyMatch(Objects::isNull)) {
            throw new ServerWebInputException("users 不能包含空项。");
        }
        body.users().forEach(item ->
                validateFeishuCreate(item.providerUserId(), item.username(), item.userGroup()));
    }

    // 提取用户创建失败的可展示原因
    private static String createFailureMessage(Throwable error) {
        if (error instanceof ResponseStatusException status && StringUtils.hasText(status.getReason())) {
            return status.getReason();
        }
        return "创建失败，请稍后重试。";
    }

    // 组装本地用户创建的审计快照
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

    // 更新当前用户的可编辑资料
    private Mono<ServerResponse> updateSelf(ServerRequest request) {
        return resolveName(request)
                .flatMap(username -> request.bodyToMono(UpdateProfileRequest.class)
                        .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                        .flatMap(update -> applyProfileUpdate(request.exchange(), username, update, false)))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    // 更新指定用户的可编辑资料
    private Mono<ServerResponse> updateUser(ServerRequest request) {
        return resolveName(request)
                .flatMap(username -> request.bodyToMono(UpdateProfileRequest.class)
                        .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                        .flatMap(update -> applyProfileUpdate(request.exchange(), username, update, true)))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    // 拒绝修改只读字段，仅允许更新简介和登录名
    private Mono<User> applyProfileUpdate(
            org.springframework.web.server.ServerWebExchange exchange,
            String username,
            UpdateProfileRequest update,
            boolean allowLoginNameChange) {
        return accounts.getUser(username).flatMap(user -> {
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
            var requestedLoginName = normalizeLoginName(update.loginName());
            if (update.loginName() != null
                    && !allowLoginNameChange
                    && !Objects.equals(requestedLoginName, user.effectiveLoginName())) {
                return Mono.error(new ServerWebInputException("登录名只能由管理员修改。"));
            }
            var applyLoginName = Mono.defer(() -> {
                if (!allowLoginNameChange || update.loginName() == null) {
                    return Mono.just(user);
                }
                if (requestedLoginName != null) {
                    validateUsername(requestedLoginName);
                }
                var nextLoginName = Objects.equals(requestedLoginName, username) ? null : requestedLoginName;
                if (Objects.equals(nextLoginName, spec.getLoginName())) {
                    return Mono.just(user);
                }
                var claimedLoginName = nextLoginName == null ? username : nextLoginName;
                return accounts
                        .isLoginNameAvailable(claimedLoginName, username)
                        .flatMap(available -> {
                            if (!Boolean.TRUE.equals(available)) {
                                return Mono.error(new ServerWebInputException(
                                        "登录名 " + claimedLoginName + " 已被占用。"));
                            }
                            spec.setLoginName(nextLoginName);
                            changedFields.add("loginName");
                            return Mono.just(user);
                        });
            });
            return applyLoginName.flatMap(ignored -> client.update(user)).doOnNext(updated -> {
                if (!changedFields.isEmpty()) {
                    publishUpdated(exchange, updated, changedFields);
                }
            });
        });
    }

    // 把输入规范为小写登录名，空白表示恢复资源名称
    private static String normalizeLoginName(String loginName) {
        return StringUtils.hasText(loginName) ? loginName.trim().toLowerCase(Locale.ROOT) : null;
    }

    // 发布来源为本地资料的用户更新事件
    private void publishUpdated(
            org.springframework.web.server.ServerWebExchange exchange, User user, List<String> changedFields) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", user.getMetadata().getName());
        payload.put("loginName", user.effectiveLoginName());
        payload.put("uid", user.getSpec().getUid());
        payload.put("displayName", user.getSpec().getDisplayName());
        payload.put("email", user.getSpec().getEmail());
        payload.put("changedFields", changedFields);
        payload.put("source", "local");
        domainEventPublisher.publish(
                "user.updated",
                payload,
                (String) exchange.getAttributes().get(AuditLog.TRACE_FLOW_ID_ATTRIBUTE));
    }

    // 先检查内置用户保护再删除
    private Mono<ServerResponse> deleteUser(ServerRequest request) {
        return resolveName(request)
                .flatMap(name -> client.get(User.class, name))
                .flatMap(user -> builtinEntityPolicy.checkUserDeletion(user).thenReturn(user))
                .flatMap(client::delete)
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    // 返回当前用户的角色与权限
    private Mono<ServerResponse> selfPermissions(ServerRequest request) {
        return currentUsername().flatMap(this::buildPermission);
    }

    // 返回当前用户聚合后的界面权限
    private Mono<ServerResponse> uiPermissions(ServerRequest request) {
        return currentUsername()
                .flatMap(uiPermissionService::uiPermissionsOf)
                .flatMap(permissions -> ServerResponse.ok().bodyValue(permissions));
    }

    // 返回指定用户的角色与权限
    private Mono<ServerResponse> userPermissions(ServerRequest request) {
        return resolveName(request).flatMap(this::buildPermission);
    }

    // 展开角色得到权限集合并构造响应
    private Mono<ServerResponse> buildPermission(String username) {
        return userRoles
                .list(username)
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

    // 修改当前用户密码时校验旧密码
    private Mono<ServerResponse> changeOwnPassword(ServerRequest request) {
        return currentUsername().flatMap(username -> changePassword(request, username, true));
    }

    // 管理员改密前检查内置用户保护
    private Mono<ServerResponse> changePasswordForUser(ServerRequest request) {
        return resolveName(request).flatMap(username -> builtinEntityPolicy
                .checkUserPasswordReset(username)
                .then(changePassword(request, username, false)));
    }

    // 按需要校验旧密码后更新密码
    private Mono<ServerResponse> changePassword(ServerRequest request, String username, boolean verifyOldPassword) {
        return request
                .bodyToMono(PasswordRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为空")))
                .flatMap(password -> verifyOldPassword
                        ? accounts
                                .confirmPassword(username, password.oldPassword())
                                .flatMap(confirmed -> Boolean.TRUE.equals(confirmed)
                                        ? accounts.updateWithRawPassword(username, password.password())
                                        : Mono.error(new UnsatisfiedAttributeValueException("旧密码不匹配。")))
                        : accounts.updateWithRawPassword(username, password.password()))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    // 返回用户所属组，未加入时报错
    private Mono<ServerResponse> getUserGroup(ServerRequest request) {
        return resolveName(request)
                .flatMap(groups::getUserGroupByUsername)
                .flatMap(group -> ServerResponse.ok().bodyValue(group))
                .switchIfEmpty(Mono.error(new ServerWebInputException("用户未加入任何用户组。")));
    }

    // 返回用户的外部身份连接视图
    private Mono<ServerResponse> listUserConnections(ServerRequest request) {
        return resolveName(request)
                .flatMap(accounts::getUser)
                .flatMapMany(user -> userConnectionService.listConnections(user.getMetadata().getName()))
                .map(UserConnectionView::from)
                .collectList()
                .flatMap(connections -> ServerResponse.ok().bodyValue(connections));
    }

    // 统计指定天数内的每日活动量
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
                .flatMap(accounts::getUser)
                .flatMap(user -> {
                    var end = LocalDate.now(PudansTime.BEIJING);
                    var start = end.minusDays(activityDays - 1L);
                    var uid = user.getSpec().getUid();
                    var counts = StringUtils.hasText(uid)
                            ? auditLog.countPerDay(
                                    PudansTime.startOfDay(start), uid, null, null)
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

    // 将用户替换分配到指定用户组
    private Mono<ServerResponse> assignUserGroup(ServerRequest request) {
        return resolveName(request)
                .flatMap(username -> request.bodyToMono(AssignGroupRequest.class)
                        .flatMap(body -> groups
                                .assignUserGroup(username, body.userGroup())
                                .then(accounts.getUser(username))))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    // 将用户从当前用户组移除
    private Mono<ServerResponse> removeUserGroup(ServerRequest request) {
        return resolveName(request)
                .flatMap(username -> groups.removeUserGroup(username).then(accounts.getUser(username)))
                .flatMap(user -> ServerResponse.ok().bodyValue(cn.tolim.pudans.core.user.endpoint.UserView.from(user)));
    }

    // 返回当前用户接口版本
    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }

    
    public record CreateUserRequest(
            String username,
            @Schema(description = "Ignored on create. The nickname is synced from Feishu.") String displayName,
            @Schema(description = "Ignored on create. The email is synced from Feishu.") String email,
            String openId,
            String unionId,
            String avatar,
            String userGroup) {
        // 优先使用联合标识，缺失时回退开放标识
        String providerUserId() {
            return StringUtils.hasText(unionId) ? unionId : openId;
        }

        // 转换为飞书账号创建所需的资料
        FeishuAccounts.FeishuUserData toFeishuData() {
            return new FeishuAccounts.FeishuUserData(username, displayName, email, openId, unionId, avatar);
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
            @Schema(description = "Ignored on create. The nickname is synced from Feishu.") String displayName,
            @Schema(description = "Ignored on create. The email is synced from Feishu.") String email,
            String openId,
            String unionId,
            String avatar,
            String userGroup) {
        // 优先使用联合标识，缺失时回退开放标识
        String providerUserId() {
            return StringUtils.hasText(unionId) ? unionId : openId;
        }

        // 转换为飞书账号创建所需的资料
        FeishuAccounts.FeishuUserData toFeishuData() {
            return new FeishuAccounts.FeishuUserData(username, displayName, email, openId, unionId, avatar);
        }
    }

    public record BatchCreateFailure(int index, String providerUserId, String message) {}

    public record BatchCreateResponse(
            int created, int failed, List<BatchCreateFailure> failures, List<UserView> users) {}

    private record BatchCreateItem(UserView user, BatchCreateFailure failure) {}

    public record FeishuContactView(
            String providerUserId,
            String openId,
            String unionId,
            String displayName,
            String email,
            String phone,
            String avatar,
            boolean imported,
            String importedUsername) {}

    public record FeishuContactPage(List<FeishuContactView> items, String pageToken, boolean hasMore) {}

    
    public record UpdateProfileRequest(
            String displayName,
            String email,
            String avatar,
            String phone,
            String bio,
            @Schema(description = "Local login name. Blank resets it to metadata.name. Changes require the administrator endpoint.")
            String loginName) {}

    
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
        // 将身份连接转换为接口视图
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
            @Schema(description = "Effective local login name.")
            String loginName,
            String displayName,
            String avatar,
            String email,
            String phone,
            Instant registeredAt,
            boolean disabled,
            String userGroup,
            String userGroupDisplayName) {
        // 默认展示邮箱生成列表项
        static ListedUser from(User user) {
            return from(user, true, null);
        }

        // 按邮箱可见性生成列表项
        static ListedUser from(User user, boolean exposeEmail) {
            return from(user, exposeEmail, null);
        }

        // 附带用户组信息生成列表项
        static ListedUser from(User user, boolean exposeEmail, UserGroup group) {
            return new ListedUser(
                    user.getMetadata().getName(),
                    user.effectiveLoginName(),
                    user.getSpec().getDisplayName(),
                    user.getSpec().getAvatar(),
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
