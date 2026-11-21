package cn.tolim.pudans.core.security.authentication.preauth;

import static cn.tolim.pudans.contract.infra.ValidationUtils.validate;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.Unstructured;
import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.contract.infra.ValidationUtils;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.infra.exception.DuplicateNameException;
import cn.tolim.pudans.core.infra.exception.ProblemDetailFactory;
import cn.tolim.pudans.core.infra.exception.RequestBodyValidationException;
import cn.tolim.pudans.core.infra.setting.ExternalUrlChangedEvent;
import cn.tolim.pudans.core.infra.setting.InitializationStateGetter;
import cn.tolim.pudans.core.infra.setting.ParameterSettingFetcher;
import cn.tolim.pudans.core.infra.setting.SystemState;
import cn.tolim.pudans.core.infra.utils.PudansUtils;
import cn.tolim.pudans.core.infra.utils.YamlUnstructuredLoader;
import cn.tolim.pudans.core.security.authentication.SuperAdminInitializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.util.InMemoryResource;
import org.springframework.stereotype.Component;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Slf4j
@Component
public class SystemSetupEndpoint {
    static final PropertyPlaceholderHelper PROPERTY_PLACEHOLDER_HELPER = new PropertyPlaceholderHelper(
            PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_PREFIX,
            PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_SUFFIX);

    private static final String SETUP_COMPLETED = "setup-completed";

    private final InitializationStateGetter initializationStateGetter;
    private final ParameterSettingFetcher systemConfigFetcher;
    private final SuperAdminInitializer superAdminInitializer;
    private final ReactiveExtensionClient client;
    private final Validator validator;
    private final ApplicationEventPublisher eventPublisher;

    public SystemSetupEndpoint(
            InitializationStateGetter initializationStateGetter,
            ParameterSettingFetcher systemConfigFetcher,
            SuperAdminInitializer superAdminInitializer,
            ReactiveExtensionClient client,
            @Qualifier("webFluxValidator") Validator validator,
            ApplicationEventPublisher eventPublisher) {
        this.initializationStateGetter = initializationStateGetter;
        this.systemConfigFetcher = systemConfigFetcher;
        this.superAdminInitializer = superAdminInitializer;
        this.client = client;
        this.validator = validator;
        this.eventPublisher = eventPublisher;
    }

    private static Mono<ServerResponse> handleSetupSuccessfully() {
        return ServerResponse.noContent().build();
    }

    private static String mergeToBasicConfig(SetupRequest body, ConfigMap configMap) {
        Map<String, String> data = configMap.getData();
        if (data == null) {
            data = new LinkedHashMap<>();
            configMap.setData(data);
        }
        String basic = data.getOrDefault(ParameterSetting.Basic.GROUP, "{}");
        var basicSetting = JsonUtils.jsonToObject(basic, ParameterSetting.Basic.class);


        if (!StringUtils.hasText(basicSetting.getTitle())) {
            basicSetting.setTitle(body.getSiteTitle());
        }
        if (!StringUtils.hasText(basicSetting.getExternalUrl())) {
            basicSetting.setExternalUrl(body.getExternalUrl());
        }
        data.put(ParameterSetting.Basic.GROUP, JsonUtils.objectToJson(basicSetting));
        return basicSetting.getExternalUrl();
    }

    private static String mergeToSiteConfig(SetupRequest body, ConfigMap configMap) {
        Map<String, String> data = configMap.getData();
        if (data == null) {
            data = new LinkedHashMap<>();
            configMap.setData(data);
        }
        String siteJson = data.getOrDefault(ParameterSetting.Site.GROUP, "{}");
        var site = JsonUtils.jsonToObject(siteJson, ParameterSetting.Site.class);
        if (!StringUtils.hasText(site.getSiteName())) {
            site.setSiteName(body.getSiteTitle());
        }
        if (!StringUtils.hasText(site.getSiteUrl())) {
            site.setSiteUrl(body.getExternalUrl());
        }
        data.put(ParameterSetting.Site.GROUP, JsonUtils.objectToJson(site));
        return StringUtils.hasText(site.getSiteUrl()) ? site.getSiteUrl() : body.getExternalUrl();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    RouterFunction<ServerResponse> setupRouter() {
        final var tag = "SystemSetupV1alpha1";
        return SpringdocRouteBuilder.route()
                .POST(
                        ApiGroups.SETUP_PATH,
                        contentType(
                                MediaType.APPLICATION_FORM_URLENCODED,
                                MediaType.APPLICATION_JSON),
                        this::setup,
                        builder -> builder.operationId("SetupSystem")
                                .description("Setup system")
                                .tag(tag)
                                .requestBody(requestBodyBuilder()
                                        .required(true)
                                        .content(contentBuilder()
                                                .mediaType(MediaType.APPLICATION_JSON_VALUE)
                                                .schema(schemaBuilder().implementation(SetupRequest.class))))
                                .response(responseBuilder()
                                        .responseCode(String.valueOf(HttpStatus.NO_CONTENT.value()))
                                        .implementation(Void.class)))
                .before(PudansUtils.noCache())
                .build();
    }

    Mono<ServerResponse> setup(ServerRequest request) {
        return initializationStateGetter
                .setupRequired()
                .filter(Boolean::booleanValue)
                .flatMap(ignored -> readSetupRequest(request).flatMap(setupRequest -> {

                    var bindingResult = validate(setupRequest, validator);
                    if (bindingResult.hasErrors()) {
                        return handleValidationErrors(bindingResult);
                    }
                    return doInitialization(setupRequest, setupRequest.getUsername())
                            .then(handleSetupSuccessfully())
                            .onErrorResume(DuplicateNameException.class, error -> {
                                bindingResult.rejectValue(
                                        "username",
                                        "duplicateName",
                                        new Object[] {setupRequest.getUsername()},
                                        "用户名 " + setupRequest.getUsername() + " 已存在，请更换用户名后重试。");
                                return handleValidationErrors(bindingResult);
                            })
                            .onErrorResume(BadCredentialsException.class, error -> {
                                bindingResult.rejectValue(
                                        "password",
                                        "badCredentials",
                                        "用户名或密码错误。");
                                return handleValidationErrors(bindingResult);
                            });
                }))
                .switchIfEmpty(ServerResponse.status(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .bodyValue(ProblemDetailFactory.create(
                                HttpStatus.CONFLICT,
                                SETUP_COMPLETED,
                                "系统初始化已完成。")));
    }

    private static Mono<SetupRequest> readSetupRequest(ServerRequest request) {
        var json = request
                .headers()
                .contentType()
                .map(contentType -> contentType.isCompatibleWith(MediaType.APPLICATION_JSON))
                .orElse(false);
        return json ? request.bodyToMono(SetupRequest.class) : request.bind(SetupRequest.class);
    }

    private Mono<ServerResponse> handleValidationErrors(BindingResult bindingResult) {
        return Mono.error(new RequestBodyValidationException(bindingResult));
    }

    Mono<Void> doInitialization(SetupRequest body, String submittedUsername) {


        var setupCompleted = new AtomicBoolean(false);
        var markSetupStarted = SystemState.upsetSystemState(client, state -> {
            if (isTrue(state.getIsSetup())) {

                setupCompleted.set(true);
                return;
            }
            state.setIsSetup(false);


            if (!StringUtils.hasText(state.getSetupUsername())) {
                state.setSetupUsername(submittedUsername);
            }
        });
        return checkSetupUsernameAvailable(submittedUsername)
                .then(markSetupStarted)
                .then(Mono.defer(() -> {
                    if (setupCompleted.get()) {
                        log.info("系统初始化已完成，跳过初始化。");
                        return Mono.empty();
                    }
                    return resolveSetupUsername(submittedUsername).flatMap(username -> doInitialize(body, username));
                }));
    }

    private Mono<Void> checkSetupUsernameAvailable(String submittedUsername) {
        return client.fetch(ConfigMap.class, SystemState.SYSTEM_STATES_CONFIGMAP)
                .map(SystemState::deserialize)
                .defaultIfEmpty(new SystemState())
                .filter(state -> !isTrue(state.getIsSetup()) && !StringUtils.hasText(state.getSetupUsername()))
                .flatMap(state -> client.fetch(User.class, submittedUsername))
                .flatMap(user -> Mono.error(new DuplicateNameException("用户名已存在。")))
                .then();
    }

    private Mono<Void> doInitialize(SetupRequest body, String username) {
        var superUserMono = Mono.defer(
                        () -> superAdminInitializer.initialize(SuperAdminInitializer.InitializationParam.builder()
                                .username(username)
                                .password(body.getPassword())
                                .email(body.getEmail())
                                .build()))
                .subscribeOn(Schedulers.boundedElastic());

        var basicConfigMono = Mono.defer(() -> systemConfigFetcher
                        .getConfigMap()
                        .switchIfEmpty(
                                Mono.error(
                                        () -> new IllegalStateException(
                                                "系统配置 ConfigMap 缺失，无法"
                                                        + "初始化基础设置。")))
                        .flatMap(configMap -> {
                            mergeToBasicConfig(body, configMap);
                            var effectiveExternalUrl = mergeToSiteConfig(body, configMap);
                            return client.update(configMap).thenReturn(effectiveExternalUrl);
                        }))
                .retryWhen(Retry.backoff(5, Duration.ofMillis(100))
                        .filter(t -> t instanceof OptimisticLockingFailureException))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(effectiveExternalUrl -> StringUtils.hasText(effectiveExternalUrl)
                        ? Mono.fromCallable(() -> {
                    eventPublisher.publishEvent(new ExternalUrlChangedEvent(
                            this, URI.create(effectiveExternalUrl).toURL()));
                    return effectiveExternalUrl;
                })
                        : Mono.empty())
                .then();


        return superUserMono
                .then(basicConfigMono)
                .then(Mono.defer(() -> initializeNecessaryData(username)))
                .then(Mono.defer(() -> SystemState.upsetSystemState(client, state -> state.setIsSetup(true))));
    }

    private Mono<Void> initializeNecessaryData(String username) {
        return loadPresetExtensions(username)
                .concatMap(extension -> client.create(extension).onErrorResume(DuplicateNameException.class, e -> {

                    log.debug(
                            "跳过创建已存在的扩展 {}/{}",
                            extension.groupVersionKind(),
                            extension.getMetadata().getName());
                    return Mono.empty();
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    Mono<String> resolveSetupUsername(String submittedUsername) {
        return findSetupUsernameInProgress().defaultIfEmpty(submittedUsername);
    }

    private Mono<String> findSetupUsernameInProgress() {
        return client.fetch(ConfigMap.class, SystemState.SYSTEM_STATES_CONFIGMAP)
                .map(SystemState::deserialize)
                .mapNotNull(SystemState::getSetupUsername)
                .filter(StringUtils::hasText);
    }

    Flux<Unstructured> loadPresetExtensions(String username) {
        return Mono.fromCallable(() -> {

                    String rawContent = PudansUtils.readClassPathResourceAsString("initial-data.yaml");

                    var properties = new Properties();
                    properties.setProperty("username", username);
                    properties.setProperty("timestamp", Instant.now().toString());

                    var processedContent = PROPERTY_PLACEHOLDER_HELPER.replacePlaceholders(rawContent, properties);

                    var stringResource = new InMemoryResource(processedContent.getBytes(StandardCharsets.UTF_8));
                    var loader = new YamlUnstructuredLoader(stringResource);
                    return loader.load();
                })
                .flatMapMany(Flux::fromIterable)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Data
    static class SetupRequest {

        @Schema(requiredMode = REQUIRED, minLength = 4, maxLength = 63)
        @NotBlank
        @Size(min = 4, max = 63)
        @Pattern(
                regexp = ValidationUtils.NAME_REGEX,
                message = "用户名只能小写且只能包含字母、数字、中划线和点，以字符开头和结尾")
        private String username;

        @Schema(requiredMode = REQUIRED, minLength = 8, maxLength = 257)
        @NotBlank
        @Pattern(
                regexp = ValidationUtils.PASSWORD_REGEX,
                message = "密码只能使用大小写字母 (A-Z, a-z)、数字 (0-9)，以及以下特殊字符: !@#$%^&*.?")
        @Size(min = 8, max = 257)
        private String password;

        @Email
        private String email;

        @NotBlank
        @Size(max = 80)
        private String siteTitle;

        @NotNull
        @jakarta.validation.constraints.Pattern(regexp = "^(http|https).*")
        private String externalUrl;
    }
}
