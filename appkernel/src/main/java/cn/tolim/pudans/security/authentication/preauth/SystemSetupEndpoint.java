package cn.tolim.pudans.security.authentication.preauth;

import static cn.tolim.pudans.contract.infra.ValidationUtils.validate;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.Unstructured;
import cn.tolim.pudans.contract.infra.ExternalUrlSupplier;
import cn.tolim.pudans.contract.infra.SystemSetting;
import cn.tolim.pudans.contract.infra.ValidationUtils;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.infra.exception.DuplicateNameException;
import cn.tolim.pudans.infra.exception.RequestBodyValidationException;
import cn.tolim.pudans.infra.setting.ExternalUrlChangedEvent;
import cn.tolim.pudans.infra.setting.InitializationStateGetter;
import cn.tolim.pudans.infra.setting.SystemConfigFetcher;
import cn.tolim.pudans.infra.setting.SystemState;
import cn.tolim.pudans.infra.utils.PudansUtils;
import cn.tolim.pudans.infra.utils.YamlUnstructuredLoader;
import cn.tolim.pudans.plugin.PluginService;
import cn.tolim.pudans.security.authentication.SuperAdminInitializer;
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
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.fn.builders.content.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcConnectionDetails;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.util.InMemoryResource;
import org.springframework.stereotype.Component;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BeanPropertyBindingResult;
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
@RequiredArgsConstructor
public class SystemSetupEndpoint {
    static final String SETUP_TEMPLATE = "setup";
    static final PropertyPlaceholderHelper PROPERTY_PLACEHOLDER_HELPER = new PropertyPlaceholderHelper(
            PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_PREFIX,
            PlaceholderConfigurerSupport.DEFAULT_PLACEHOLDER_SUFFIX);

    private final InitializationStateGetter initializationStateGetter;
    private final SystemConfigFetcher systemConfigFetcher;
    private final SuperAdminInitializer superAdminInitializer;
    private final ReactiveExtensionClient client;
    private final PluginService pluginService;
    private final Validator validator;
    private final ObjectProvider<R2dbcConnectionDetails> connectionDetails;
    private final ExternalUrlSupplier externalUrl;
    private final ApplicationEventPublisher eventPublisher;

    private static Mono<ServerResponse> handleSetupSuccessfully(ServerRequest request) {
        if (isHtmlRequest(request)) {
            return redirectToConsole();
        }
        return ServerResponse.noContent().build();
    }

    private static boolean isHtmlRequest(ServerRequest request) {
        return request.headers().accept().contains(MediaType.TEXT_HTML)
                && !PudansUtils.isXhr(request.headers().asHttpHeaders());
    }

    private static Mono<ServerResponse> redirectToConsole() {
        return ServerResponse.status(HttpStatus.FOUND)
                .location(URI.create("/auth/"))
                .build();
    }

    private static String mergeToBasicConfig(SetupRequest body, ConfigMap configMap) {
        Map<String, String> data = configMap.getData();
        if (data == null) {
            data = new LinkedHashMap<>();
            configMap.setData(data);
        }
        String basic = data.getOrDefault(SystemSetting.Basic.GROUP, "{}");
        var basicSetting = JsonUtils.jsonToObject(basic, SystemSetting.Basic.class);


        if (!StringUtils.hasText(basicSetting.getTitle())) {
            basicSetting.setTitle(body.getSiteTitle());
        }
        if (!StringUtils.hasText(basicSetting.getLanguage())) {
            basicSetting.setLanguage(body.getLanguage());
        }
        if (!StringUtils.hasText(basicSetting.getExternalUrl())) {
            basicSetting.setExternalUrl(body.getExternalUrl());
        }
        data.put(SystemSetting.Basic.GROUP, JsonUtils.objectToJson(basicSetting));
        return basicSetting.getExternalUrl();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    RouterFunction<ServerResponse> setupPageRouter() {
        final var tag = "SystemV1alpha1Public";
        return SpringdocRouteBuilder.route()
                .GET(
                        path(ApiGroups.PUBLIC_SETUP_PATH).and(accept(MediaType.TEXT_HTML)),
                        this::setupPage,
                        builder -> builder.operationId("JumpToSetupPage")
                                .description("Jump to setup page")
                                .tag(tag)
                                .response(responseBuilder()
                                        .content(Builder.contentBuilder().mediaType(MediaType.TEXT_HTML_VALUE))
                                        .implementation(String.class)))
                .POST(
                        ApiGroups.PUBLIC_SETUP_PATH,
                        contentType(MediaType.APPLICATION_FORM_URLENCODED),
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
                .flatMap(ignored -> request.bind(SetupRequest.class).flatMap(setupRequest -> {

                    var bindingResult = validate(setupRequest, validator, request.exchange());
                    if (bindingResult.hasErrors()) {
                        return handleValidationErrors(bindingResult, request);
                    }
                    return doInitialization(setupRequest, setupRequest.getUsername())
                            .then(handleSetupSuccessfully(request))
                            .onErrorResume(DuplicateNameException.class, error -> {
                                bindingResult.rejectValue(
                                        "username",
                                        "problemDetail.user.duplicateName",
                                        new Object[] {setupRequest.getUsername()},
                                        "The username already exists.");
                                return handleValidationErrors(bindingResult, request);
                            })
                            .onErrorResume(BadCredentialsException.class, error -> {
                                bindingResult.rejectValue(
                                        "password",
                                        "problemDetail.org.springframework.security.authentication"
                                                + ".BadCredentialsException",
                                        "The username or password is incorrect.");
                                return handleValidationErrors(bindingResult, request);
                            });
                }))
                .switchIfEmpty(redirectToConsole());
    }

    private Mono<ServerResponse> handleValidationErrors(BindingResult bindingResult, ServerRequest request) {
        if (isHtmlRequest(request)) {
            return findSetupUsernameInProgress()
                    .map(username -> true)
                    .defaultIfEmpty(false)
                    .flatMap(resumingSetup -> {
                        var model = bindingResult.getModel();
                        model.put("resumingSetup", resumingSetup);
                        model.put("usingH2database", usingH2database());
                        return ServerResponse.status(HttpStatus.BAD_REQUEST).render(SETUP_TEMPLATE, model);
                    });
        }
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
                        log.info("System setup has already been completed, skip the initialization.");
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
                .flatMap(user -> Mono.error(new DuplicateNameException("The username already exists.")))
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
                                                "The system config ConfigMap is missing, so the basic settings cannot"
                                                        + " be initialized.")))
                        .flatMap(configMap -> {
                            var effectiveExternalUrl = mergeToBasicConfig(body, configMap);
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
                .then(Mono.defer(pluginService::installPresetPlugins))

                .then(Mono.defer(() -> SystemState.upsetSystemState(client, state -> state.setIsSetup(true))));
    }

    private Mono<Void> initializeNecessaryData(String username) {
        return loadPresetExtensions(username)
                .concatMap(extension -> client.create(extension).onErrorResume(DuplicateNameException.class, e -> {

                    log.debug(
                            "Skip creating existing extension {}/{}",
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

    private Mono<ServerResponse> setupPage(ServerRequest request) {
        return initializationStateGetter.setupRequired().flatMap(setupRequired -> {
            if (!setupRequired) {
                return redirectToConsole();
            }
            return ServerResponse.temporaryRedirect(URI.create("/auth/")).build();
        });
    }

    private boolean usingH2database() {
        var rcd = connectionDetails.getIfUnique();
        if (rcd == null) {

            return true;
        }
        var options = rcd.getConnectionFactoryOptions();
        return Optional.ofNullable(options.getValue(DRIVER))
                .map(Object::toString)
                .map("h2"::equalsIgnoreCase)
                .orElse(false);
    }

    Flux<Unstructured> loadPresetExtensions(String username) {
        return Mono.fromCallable(() -> {

                    var classPathResource = new ClassPathResource("initial-data.yaml");
                    String rawContent =
                            StreamUtils.copyToString(classPathResource.getInputStream(), StandardCharsets.UTF_8);

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
        @Pattern(regexp = ValidationUtils.NAME_REGEX, message = "{validation.error.username.pattern}")
        private String username;

        @Schema(requiredMode = REQUIRED, minLength = 5, maxLength = 257)
        @NotBlank
        @Pattern(regexp = ValidationUtils.PASSWORD_REGEX, message = "{validation.error.password.pattern}")
        @Size(min = 5, max = 257)
        private String password;

        @Email
        private String email;

        @NotBlank
        @Size(max = 80)
        private String siteTitle;

        @Pattern(regexp = "^(zh-CN|zh-TW|en|es)$")
        private String language;

        @NotNull
        @jakarta.validation.constraints.Pattern(regexp = "^(http|https).*")
        private String externalUrl;
    }
}
