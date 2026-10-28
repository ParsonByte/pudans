package cn.tolim.pudans.plugin.reconciler;

import static cn.tolim.pudans.contract.core.extension.Plugin.PluginStatus.nullSafeConditions;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.addFinalizers;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.removeFinalizers;
import static cn.tolim.pudans.contract.extension.MetadataUtil.nullSafeAnnotations;
import static cn.tolim.pudans.plugin.PluginConst.PLUGIN_PATH;
import static cn.tolim.pudans.plugin.PluginConst.RELOAD_ANNO;
import static cn.tolim.pudans.plugin.PluginConst.REQUEST_TO_UNLOAD_LABEL;
import static cn.tolim.pudans.plugin.PluginExtensionLoaderUtils.isSetting;
import static cn.tolim.pudans.plugin.PluginExtensionLoaderUtils.lookupExtensions;
import static cn.tolim.pudans.plugin.PluginUtils.generateFileName;
import static cn.tolim.pudans.plugin.PluginUtils.isDevelopmentMode;

import cn.tolim.pudans.contract.core.extension.Plugin;
import cn.tolim.pudans.contract.core.extension.ReverseProxy;
import cn.tolim.pudans.contract.core.extension.Setting;
import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.ExtensionUtil;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.MetadataUtil;
import cn.tolim.pudans.contract.extension.Unstructured;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import cn.tolim.pudans.contract.extension.controller.Reconciler.Request;
import cn.tolim.pudans.contract.extension.controller.RequeueException;
import cn.tolim.pudans.contract.infra.Condition;
import cn.tolim.pudans.contract.infra.ConditionList;
import cn.tolim.pudans.contract.infra.ConditionStatus;
import cn.tolim.pudans.contract.infra.utils.PathUtils;
import cn.tolim.pudans.contract.plugin.SpringPluginManager;
import cn.tolim.pudans.infra.utils.SettingUtils;
import cn.tolim.pudans.infra.utils.VersionUtils;
import cn.tolim.pudans.infra.utils.YamlUnstructuredLoader;
import cn.tolim.pudans.plugin.OptionalDependentResolver;
import cn.tolim.pudans.plugin.PluginConst;
import cn.tolim.pudans.plugin.PluginProperties;
import cn.tolim.pudans.plugin.PluginService;
import cn.tolim.pudans.plugin.YamlPluginFinder;
import cn.tolim.pudans.plugin.resources.BundleResourceUtils;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.pf4j.RuntimeMode;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;


@Slf4j
@Component
@RequiredArgsConstructor
class PluginReconciler implements Reconciler<Request>, DisposableBean {
    private static final String FINALIZER_NAME = "plugin-protection";

    private static final Set<String> UNUSED_ANNOTATIONS = Set.of("plugin.pudans.tolim.cn/dependents-snapshot");

    private final ExtensionClient client;

    private final SpringPluginManager pluginManager;

    private final PluginProperties pluginProperties;

    private final PluginService pluginService;

    private final ConcurrentMap<String, Disposable> pluginStartTasks = new ConcurrentHashMap<>();

    private Scheduler scheduler =
            Schedulers.newBoundedElastic(1, Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE, "plugin-starter");

    private Clock clock = Clock.systemUTC();

    private static String requestToUnload(Plugin plugin) {
        var labels = plugin.getMetadata().getLabels();
        if (labels == null) {
            return null;
        }
        return labels.get(REQUEST_TO_UNLOAD_LABEL);
    }

    private static boolean requestToReload(Plugin plugin) {
        var annotations = plugin.getMetadata().getAnnotations();
        return annotations != null && annotations.get(RELOAD_ANNO) != null;
    }

    private static void removeRequestToReload(Plugin plugin) {
        var annotations = plugin.getMetadata().getAnnotations();
        if (annotations != null) {
            annotations.remove(RELOAD_ANNO);
        }
    }

    private static boolean requestToEnable(Plugin plugin) {
        var enabled = plugin.getSpec().getEnabled();
        return enabled != null && enabled;
    }

    static String buildReverseProxyName(String pluginName) {
        return pluginName + "-system-generated-reverse-proxy";
    }

    private static void removeConditionBy(ConditionList conditions, String type) {
        conditions.removeIf(condition -> Objects.equals(type, condition.getType()));
    }

    @Override
    public void destroy() throws Exception {
        pluginStartTasks.clear();
        this.scheduler.dispose();
    }

    void setClock(Clock clock) {
        Assert.notNull(clock, "clock must not be null");
        this.clock = clock;
    }

    void setScheduler(Scheduler scheduler) {
        Assert.notNull(scheduler, "scheduler must not be null");
        this.scheduler = scheduler;
    }

    @Override
    public Result reconcile(Request request) {
        return client.fetch(Plugin.class, request.name())
                .map(plugin -> {
                    if (ExtensionUtil.isDeleted(plugin)) {
                        if (!checkDependents(plugin)) {
                            client.update(plugin);

                            return Result.requeue(Duration.ofSeconds(10));
                        }

                        if (removeFinalizers(plugin.getMetadata(), Set.of(FINALIZER_NAME))) {
                            cleanupResources(plugin);
                            syncPluginState(plugin);
                            client.update(plugin);
                        }
                        return Result.doNotRetry();
                    }
                    addFinalizers(plugin.getMetadata(), Set.of(FINALIZER_NAME));
                    removeUnusedAnnotations(plugin);

                    var status = plugin.getStatus();
                    if (status == null) {
                        status = new Plugin.PluginStatus();
                        plugin.setStatus(status);
                    }
                    if (status.getPhase() == null) {

                        status.setPhase(Plugin.Phase.PENDING);
                    }

                    if (status.getConditions() == null) {
                        status.setConditions(new ConditionList());
                    }

                    var steps = new LinkedList<Supplier<Result>>();
                    steps.add(() -> resolveLoadLocation(plugin));
                    steps.add(() -> loadOrReload(plugin));
                    steps.add(() -> createOrUpdateSetting(plugin));
                    steps.add(() -> createOrUpdateReverseProxy(plugin));
                    steps.add(() -> resolveStaticResources(plugin));
                    if (requestToEnable(plugin)) {
                        steps.add(() -> enablePlugin(plugin));
                    } else {
                        steps.add(() -> disablePlugin(plugin));
                    }

                    Result result = null;
                    try {
                        for (var step : steps) {
                            result = step.get();
                            if (result != null) {
                                break;
                            }
                        }
                        return result;
                    } catch (Throwable e) {
                        status.getConditions()
                                .addAndEvictFIFO(Condition.builder()
                                        .type(ConditionType.READY)
                                        .status(ConditionStatus.FALSE)
                                        .reason(ConditionReason.SYSTEM_ERROR)
                                        .message(e.getMessage())
                                        .lastTransitionTime(clock.instant())
                                        .build());
                        status.setPhase(Plugin.Phase.UNKNOWN);
                        throw e;
                    } finally {
                        var pw = pluginManager.getPlugin(plugin.getMetadata().getName());
                        if (pw != null) {
                            status.setLastProbeState(pw.getPluginState());
                        }
                        client.update(plugin);
                    }
                })
                .orElseGet(Result::doNotRetry);
    }

    private void removeUnusedAnnotations(Plugin plugin) {
        var annotations = plugin.getMetadata().getAnnotations();
        if (annotations != null) {
            UNUSED_ANNOTATIONS.forEach(annotations::remove);
        }
    }

    private boolean checkDependents(Plugin plugin) {
        var pluginId = plugin.getMetadata().getName();
        var dependents = pluginManager.getDependents(pluginId);
        if (CollectionUtils.isEmpty(dependents)) {
            return true;
        }
        var status = plugin.statusNonNull();
        var condition = Condition.builder()
                .type(ConditionType.PROGRESSING)
                .status(ConditionStatus.UNKNOWN)
                .reason(ConditionReason.WAIT_FOR_DEPENDENTS_DELETED)
                .message("The plugin has dependents %s, please delete them first."
                        .formatted(dependents.stream()
                                .map(PluginWrapper::getPluginId)
                                .toList()))
                .lastTransitionTime(clock.instant())
                .build();
        var conditions = nullSafeConditions(status);
        removeConditionBy(conditions, ConditionType.INITIALIZED);
        removeConditionBy(conditions, ConditionType.READY);
        conditions.addAndEvictFIFO(condition);
        status.setPhase(Plugin.Phase.UNKNOWN);
        return false;
    }

    private void syncPluginState(Plugin plugin) {
        var pluginName = plugin.getMetadata().getName();
        var p = pluginManager.getPlugin(pluginName);
        if (p != null) {
            plugin.statusNonNull().setLastProbeState(p.getPluginState());
        } else {
            plugin.statusNonNull().setLastProbeState(null);
        }
    }

    private void cleanupResources(Plugin plugin) {
        var pluginName = plugin.getMetadata().getName();
        var reverseProxyName = buildReverseProxyName(pluginName);
        log.info("Deleting reverse proxy {} for plugin {}", reverseProxyName, pluginName);
        client.fetch(ReverseProxy.class, reverseProxyName).ifPresent(reverseProxy -> {
            client.delete(reverseProxy);
            throw new RequeueException(Result.requeue(null), String.format("""
                    Waiting for reverse proxy %s to be deleted.""", reverseProxyName));
        });
        var settingName = plugin.getSpec().getSettingName();
        if (StringUtils.isNotBlank(settingName)) {
            log.info("Deleting settings {} for plugin {}", settingName, pluginName);
            client.fetch(Setting.class, settingName).ifPresent(setting -> {
                client.delete(setting);
                throw new RequeueException(Result.requeue(null), String.format("""
                        Waiting for setting %s to be deleted.""", settingName));
            });
        }
        if (pluginManager.getPlugin(pluginName) != null) {
            log.info("Deleting plugin {} in plugin manager.", pluginName);
            var deleted = pluginManager.deletePlugin(pluginName);
            if (!deleted) {
                throw new RequeueException(
                        Result.requeue(Duration.ofSeconds(10)),
                        "Failed to delete plugin " + pluginName + " in plugin manager");
            }
        }

        cleanUpOrphanedJars(pluginName);
    }

    private void cleanUpOrphanedJars(String pluginName) {
        var prefix = pluginName + "-";
        for (var pluginRoot : pluginManager.getPluginsRoots()) {
            if (!Files.exists(pluginRoot)) {
                continue;
            }
            try (var files = Files.list(pluginRoot)) {
                files.filter(f -> {
                            var fn = f.getFileName().toString();
                            return fn.startsWith(prefix) && fn.endsWith(".jar");
                        })
                        .forEach(jar -> {
                            var name = readPluginNameFromJar(jar);
                            if (pluginName.equals(name)) {
                                try {
                                    log.info("Deleting orphaned plugin JAR {}", jar);
                                    Files.deleteIfExists(jar);
                                } catch (IOException e) {
                                    throw new RequeueException(
                                            Result.requeue(Duration.ofSeconds(10)),
                                            "Failed to delete orphaned JAR " + jar + " for plugin " + pluginName);
                                }
                            }
                        });
            } catch (IOException e) {
                log.warn("Failed to list plugins directory {} for cleanup", pluginRoot, e);
            }
        }
    }

    private String readPluginNameFromJar(Path jarPath) {
        try {

            return new YamlPluginFinder().find(jarPath).getMetadata().getName();
        } catch (Exception e) {
            log.warn("Failed to read manifest from {}", jarPath, e);
            return null;
        }
    }

    private Result enablePlugin(Plugin plugin) {

        var pluginName = plugin.getMetadata().getName();
        var status = plugin.getStatus();
        var conditions = status.getConditions();
        log.info("Starting plugin {}", pluginName);


        var unstartedDependencies = pluginService.getRequiredDependencies(
                plugin, pw -> pw == null || !PluginState.STARTED.equals(pw.getPluginState()));
        if (!CollectionUtils.isEmpty(unstartedDependencies)) {
            removeConditionBy(conditions, ConditionType.READY);
            conditions.addAndEvictFIFO(Condition.builder()
                    .type(ConditionType.PROGRESSING)
                    .status(ConditionStatus.UNKNOWN)
                    .reason(ConditionReason.WAIT_FOR_DEPENDENCIES_STARTED)
                    .message("Wait for parent plugins " + unstartedDependencies + " to be started")
                    .lastTransitionTime(clock.instant())
                    .build());
            status.setPhase(Plugin.Phase.UNKNOWN);
            return Result.requeue(Duration.ofSeconds(5));
        }

        var current = pluginManager.getPlugin(pluginName);
        if (current == null) {
            conditions.addAndEvictFIFO(Condition.builder()
                    .type(ConditionType.READY)
                    .status(ConditionStatus.FALSE)
                    .reason(ConditionReason.START_ERROR)
                    .message("Plugin " + pluginName + " is not loaded.")
                    .lastTransitionTime(clock.instant())
                    .build());
            status.setPhase(Plugin.Phase.FAILED);
            removeStartTaskIfPresent(pluginName);
            return Result.doNotRetry();
        }
        var pluginState = current.getPluginState();
        if (pluginState.isStarted()) {
            removeConditionBy(conditions, ConditionType.PROGRESSING);
            status.setLastStartTime(clock.instant());
            conditions.addAndEvictFIFO(Condition.builder()
                    .type(ConditionType.READY)
                    .status(ConditionStatus.TRUE)
                    .reason(ConditionReason.STARTED)
                    .message("Started successfully")
                    .lastTransitionTime(clock.instant())
                    .build());
            status.setPhase(Plugin.Phase.STARTED);
            removeStartTaskIfPresent(pluginName);
            requestToReloadPluginsOptionallyDependentOn(pluginName);
            return Result.doNotRetry();
        }
        if (pluginState.isFailed()) {
            var t = current.getFailedException();
            log.debug("Error occurred when starting plugin {}", pluginName, t);
            var writer = new StringWriter();
            t.printStackTrace(new PrintWriter(writer));
            conditions.addAndEvictFIFO(Condition.builder()
                    .type(ConditionType.READY)
                    .status(ConditionStatus.FALSE)
                    .reason(ConditionReason.START_ERROR)
                    .message(writer.toString())
                    .lastTransitionTime(clock.instant())
                    .build());
            status.setPhase(Plugin.Phase.FAILED);
            removeStartTaskIfPresent(pluginName);
            return Result.doNotRetry();
        }
        if (!Plugin.Phase.STARTING.equals(status.getPhase())) {
            pluginStartTasks.compute(pluginName, (name, old) -> {
                if (old != null && !old.isDisposed()) {
                    log.info("Cancelling old starting task for plugin {}.", name);
                    old.dispose();
                    log.info("Cancelled old starting task for plugin {}.", name);
                }
                return scheduler.schedule(() -> {
                    log.info("Starting plugin {} in background thread.", name);
                    try {
                        var state = pluginManager.startPlugin(name);
                        log.info("Plugin {} started with state {}.", name, state);
                    } catch (Throwable t) {
                        var pluginWrapper = pluginManager.getPlugin(name);
                        if (pluginWrapper != null) {
                            pluginWrapper.setPluginState(PluginState.FAILED);
                            pluginWrapper.setFailedException(t);
                        }
                    }
                });
            });
            status.setPhase(Plugin.Phase.STARTING);
            conditions.addAndEvictFIFO(Condition.builder()
                    .type(ConditionType.PROGRESSING)
                    .status(ConditionStatus.TRUE)
                    .reason(ConditionReason.STARTING)
                    .message("Starting plugin " + pluginName)
                    .lastTransitionTime(clock.instant())
                    .build());
        } else {
            log.debug("Plugin {} is starting...", pluginName);
        }
        return Result.requeue(Duration.ofSeconds(2));
    }

    private Result checkRequiresVersion(Plugin plugin) {
        var pluginName = plugin.getMetadata().getName();
        var status = plugin.getStatus();
        var conditions = status.getConditions();
        var systemVersion = pluginManager.getSystemVersion();
        var requires = plugin.getSpec().getRequires();
        if (!VersionUtils.satisfiesRequires(systemVersion, requires)) {
            removeConditionBy(conditions, ConditionType.PROGRESSING);
            conditions.addAndEvictFIFO(Condition.builder()
                    .type(ConditionType.READY)
                    .status(ConditionStatus.FALSE)
                    .reason(ConditionReason.UNSATISFIED_REQUIRES_VERSION)
                    .message("Plugin requires Pudans version [%s], but the current version is [%s]."
                            .formatted(requires, systemVersion))
                    .lastTransitionTime(clock.instant())
                    .build());
            status.setPhase(Plugin.Phase.FAILED);
            removeStartTaskIfPresent(pluginName);
            return Result.doNotRetry();
        }
        return null;
    }

    void requestToReloadPluginsOptionallyDependentOn(String pluginName) {
        var startedPlugins = pluginManager.startedPlugins().stream()
                .map(PluginWrapper::getDescriptor)
                .toList();
        var resolver = new OptionalDependentResolver(startedPlugins);
        var dependents = resolver.getOptionalDependents(pluginName);
        for (String dependentName : dependents) {
            client.fetch(Plugin.class, dependentName).ifPresent(childPlugin -> {
                var annotations = MetadataUtil.nullSafeAnnotations(childPlugin);

                annotations.put(
                        RELOAD_ANNO, childPlugin.getStatus().getLoadLocation().toString());
                client.update(childPlugin);
            });
        }
    }

    private Result disablePlugin(Plugin plugin) {
        var pluginName = plugin.getMetadata().getName();
        var status = plugin.getStatus();
        if (pluginManager.getPlugin(pluginName) != null) {

            var dependents = pluginManager.getDependents(pluginName).stream()
                    .filter(pw -> PluginState.STARTED.equals(pw.getPluginState()))
                    .map(PluginWrapper::getPluginId)
                    .toList();
            var conditions = status.getConditions();
            if (!CollectionUtils.isEmpty(dependents)) {
                removeConditionBy(conditions, ConditionType.READY);
                conditions.addAndEvictFIFO(Condition.builder()
                        .type(ConditionType.PROGRESSING)
                        .status(ConditionStatus.UNKNOWN)
                        .reason(ConditionReason.WAIT_FOR_DEPENDENTS_DISABLED)
                        .message("Wait for children plugins " + dependents + " to be disabled")
                        .lastTransitionTime(clock.instant())
                        .build());
                status.setPhase(Plugin.Phase.DISABLING);
                return Result.requeue(Duration.ofSeconds(1));
            }
            try {

                removeStartTaskIfPresent(pluginName);
                pluginManager.disablePlugin(pluginName);
            } catch (Throwable e) {
                log.error("Error occurred when disabling plugin {}", pluginName, e);
                conditions.addAndEvictFIFO(Condition.builder()
                        .type(ConditionType.READY)
                        .status(ConditionStatus.FALSE)
                        .reason(ConditionReason.DISABLE_ERROR)
                        .message(e.getMessage())
                        .lastTransitionTime(clock.instant())
                        .build());
                status.setPhase(Plugin.Phase.FAILED);
                return Result.doNotRetry();
            }
        }
        var conditions = plugin.getStatus().getConditions();
        removeConditionBy(conditions, ConditionType.PROGRESSING);
        conditions.addAndEvictFIFO(Condition.builder()
                .type(ConditionType.READY)
                .status(ConditionStatus.TRUE)
                .reason(ConditionReason.DISABLED)
                .lastTransitionTime(clock.instant())
                .build());
        plugin.statusNonNull().setPhase(Plugin.Phase.DISABLED);
        return null;
    }

    private Result resolveStaticResources(Plugin plugin) {
        var pluginName = plugin.getMetadata().getName();
        var pluginVersion = plugin.getSpec().getVersion();
        if (isDevelopmentMode(plugin)) {

            pluginVersion = String.valueOf(clock.instant().toEpochMilli());
        }
        var status = plugin.statusNonNull();
        status.setEntry(null);
        status.setStylesheet(null);
        var specLogo = plugin.getSpec().getLogo();
        if (StringUtils.isNotBlank(specLogo)) {
            log.info("Resolving logo resource for plugin {}", pluginName);


            var logo = specLogo;
            if (!specLogo.startsWith("data:image")) {
                try {
                    logo = new URL(specLogo).toString();
                } catch (MalformedURLException ignored) {

                    logo = UriComponentsBuilder.newInstance()
                            .pathSegment("plugins", pluginName, "assets")
                            .path(specLogo)
                            .queryParam("version", pluginVersion)
                            .build(true)
                            .toString();
                }
            }
            status.setLogo(logo);
        }

        log.info("Resolving main.js and style.css for plugin {}", pluginName);
        var resourceLoader = BundleResourceUtils.getResourceLoader(pluginManager, pluginName);
        if (resourceLoader == null) {
            return null;
        }
        var bundleLocation = BundleResourceUtils.selectBundleLocation(resourceLoader);
        if (bundleLocation == null) {
            return null;
        }
        var entryRes =
                BundleResourceUtils.getBundleResource(resourceLoader, bundleLocation, BundleResourceUtils.JS_BUNDLE);
        var cssRes =
                BundleResourceUtils.getBundleResource(resourceLoader, bundleLocation, BundleResourceUtils.CSS_BUNDLE);
        if (entryRes != null && entryRes.exists()) {
            var entry = UriComponentsBuilder.newInstance()
                    .pathSegment("plugins", pluginName, "assets", bundleLocation, BundleResourceUtils.JS_BUNDLE)
                    .queryParam("version", pluginVersion)
                    .build(true)
                    .toString();
            status.setEntry(entry);
        }
        if (cssRes != null && cssRes.exists()) {
            var stylesheet = UriComponentsBuilder.newInstance()
                    .pathSegment("plugins", pluginName, "assets", bundleLocation, BundleResourceUtils.CSS_BUNDLE)
                    .queryParam("version", pluginVersion)
                    .build(true)
                    .toString();
            status.setStylesheet(stylesheet);
        }
        return null;
    }

    private Result loadOrReload(Plugin plugin) {
        var pluginName = plugin.getMetadata().getName();
        var p = pluginManager.getPlugin(pluginName);
        var conditions = plugin.getStatus().getConditions();

        var requestToUnloadBy = requestToUnload(plugin);
        var requestToUnload = requestToUnloadBy != null;
        var notFullyLoaded = p != null && pluginManager.getUnresolvedPlugins().contains(p);
        var alreadyLoaded = p != null && pluginManager.getResolvedPlugins().contains(p);

        var requestToReload = requestToReload(plugin);

        var shouldUnload = requestToUnload || requestToReload || notFullyLoaded;
        if (shouldUnload) {

            if (alreadyLoaded || notFullyLoaded) {

                var dependents = requestToUnloadChildren(pluginName);
                if (!CollectionUtils.isEmpty(dependents)) {
                    removeConditionBy(conditions, ConditionType.READY);
                    conditions.addAndEvictFIFO(Condition.builder()
                            .type(ConditionType.PROGRESSING)
                            .status(ConditionStatus.UNKNOWN)
                            .reason(ConditionReason.WAIT_FOR_DEPENDENTS_UNLOADED)
                            .message("Wait for children plugins " + dependents + "to be unloaded")
                            .lastTransitionTime(clock.instant())
                            .build());
                    plugin.getStatus().setPhase(Plugin.Phase.UNKNOWN);


                    return Result.requeue(Duration.ofSeconds(1));
                }


                pluginManager.unloadPlugin(pluginName);

                removeConditionBy(conditions, ConditionType.INITIALIZED);
                removeConditionBy(conditions, ConditionType.PROGRESSING);
                removeConditionBy(conditions, ConditionType.READY);

                cancelUnloadRequest(pluginName);
                p = null;
            }


            if (requestToUnload) {

                var status = plugin.getStatus();
                status.getConditions()
                        .addAndEvictFIFO(Condition.builder()
                                .type(ConditionType.INITIALIZED)
                                .status(ConditionStatus.FALSE)
                                .reason(ConditionReason.REQUEST_TO_UNLOAD)
                                .message("Request to unload by " + requestToUnloadBy)
                                .lastTransitionTime(clock.instant())
                                .build());
                return Result.doNotRetry();
            }

            if (requestToReload) {
                removeRequestToReload(plugin);
            }
        }

        if (requestToEnable(plugin)) {
            var result = checkRequiresVersion(plugin);
            if (result != null) {
                return result;
            }
        }


        var unresolvedParentPlugins = pluginService.getRequiredDependencies(
                plugin, pw -> pw == null || pluginManager.getUnresolvedPlugins().contains(pw));
        if (!unresolvedParentPlugins.isEmpty()) {

            removeConditionBy(conditions, ConditionType.INITIALIZED);
            removeConditionBy(conditions, ConditionType.READY);
            conditions.addAndEvictFIFO(Condition.builder()
                    .type(ConditionType.PROGRESSING)
                    .status(ConditionStatus.UNKNOWN)
                    .reason(ConditionReason.WAIT_FOR_DEPENDENCIES_LOADED)
                    .message("Wait for parent plugins " + unresolvedParentPlugins + " to be loaded")
                    .lastTransitionTime(clock.instant())
                    .build());
            plugin.getStatus().setPhase(Plugin.Phase.UNKNOWN);
            return Result.requeue(Duration.ofSeconds(1));
        }

        if (p == null) {
            var loadLocation = plugin.getStatus().getLoadLocation();
            log.info("Loading plugin {} from {}", pluginName, loadLocation);
            pluginManager.loadPlugin(Paths.get(loadLocation));
            plugin.getStatus().setPhase(Plugin.Phase.RESOLVED);
            log.info("Loaded plugin {} from {}", pluginName, loadLocation);
            conditions.addAndEvictFIFO(Condition.builder()
                    .type(ConditionType.INITIALIZED)
                    .status(ConditionStatus.TRUE)
                    .reason(ConditionReason.LOADED)
                    .lastTransitionTime(clock.instant())
                    .build());
        }

        return null;
    }

    private Result createOrUpdateSetting(Plugin plugin) {
        log.info(
                "Initializing setting and config map for plugin {}",
                plugin.getMetadata().getName());
        var settingName = plugin.getSpec().getSettingName();
        if (StringUtils.isBlank(settingName)) {

            return null;
        }

        var pluginName = plugin.getMetadata().getName();
        var p = pluginManager.getPlugin(pluginName);
        var resources = lookupExtensions(p.getPluginClassLoader());
        var loader = new YamlUnstructuredLoader(resources);
        var setting = loader.load().stream()
                .filter(isSetting(settingName))
                .findFirst()
                .map(u -> Unstructured.OBJECT_MAPPER.convertValue(u, Setting.class))
                .orElseThrow(() -> new IllegalStateException(String.format("""
                        Setting name %s was provided but setting extension \
                        was not found in plugin %s.""", settingName, pluginName)));

        client.fetch(Setting.class, settingName)
                .ifPresentOrElse(
                        oldSetting -> {

                            var version = oldSetting.getMetadata().getVersion();
                            setting.getMetadata().setVersion(version);

                            removeFinalizers(setting.getMetadata(), Set.of("plugin-protector"));
                            client.update(setting);
                        },
                        () -> client.create(setting));

        log.info("Initialized setting {} for plugin {}", settingName, pluginName);


        var configMapName = plugin.getSpec().getConfigMapName();
        if (StringUtils.isBlank(configMapName)) {
            return null;
        }

        var defaultConfigMap = SettingUtils.populateDefaultConfig(setting, configMapName);

        client.fetch(ConfigMap.class, configMapName)
                .ifPresentOrElse(
                        configMap -> {

                            var oldData = configMap.getData();
                            var defaultData = defaultConfigMap.getData();
                            var mergedData = SettingUtils.mergePatch(oldData, defaultData);
                            configMap.setData(mergedData);
                            client.update(configMap);
                        },
                        () -> client.create(defaultConfigMap));
        log.info("Initialized config map {} for plugin {}", configMapName, pluginName);
        return null;
    }

    private Result resolveLoadLocation(Plugin plugin) {
        log.debug("Resolving load location for plugin {}", plugin.getMetadata().getName());


        var pluginName = plugin.getMetadata().getName();
        var annotations = nullSafeAnnotations(plugin);
        var pluginPathAnno = annotations.get(PLUGIN_PATH);
        var status = plugin.statusNonNull();
        if (isDevelopmentMode(plugin)) {
            if (!isInDevEnvironment()) {
                status.getConditions()
                        .addAndEvictFIFO(Condition.builder()
                                .type(ConditionType.INITIALIZED)
                                .status(ConditionStatus.FALSE)
                                .reason(ConditionReason.INVALID_RUNTIME_MODE)
                                .message("""
                                        Cannot run the plugin with development mode in non-development environment.\
                                        """)
                                .lastTransitionTime(clock.instant())
                                .build());
                status.setPhase(Plugin.Phase.UNKNOWN);
                return Result.doNotRetry();
            }
            log.debug("Plugin {} is in development mode", pluginName);
            if (StringUtils.isBlank(pluginPathAnno)) {
                status.getConditions()
                        .addAndEvictFIFO(Condition.builder()
                                .type(ConditionType.INITIALIZED)
                                .status(ConditionStatus.FALSE)
                                .reason(ConditionReason.PLUGIN_PATH_NOT_SET)
                                .message("""
                                        Plugin path annotation is not set. \
                                        Please set plugin path annotation "%s" in development mode.\
                                        """.formatted(PLUGIN_PATH))
                                .build());
                return Result.doNotRetry();
            }
            try {
                var loadLocation = ResourceUtils.getURL(pluginPathAnno).toURI();
                if (!Objects.equals(status.getLoadLocation(), loadLocation)) {
                    log.debug(
                            "Populated load location {} for plugin {} from annotation {}",
                            loadLocation,
                            pluginName,
                            pluginPathAnno);
                    status.setLoadLocation(loadLocation);
                    status.setPhase(Plugin.Phase.RESOLVED);
                    status.getConditions()
                            .addAndEvictFIFO(Condition.builder()
                                    .type(ConditionType.INITIALIZED)
                                    .status(ConditionStatus.TRUE)
                                    .reason(ConditionReason.LOAD_LOCATION_RESOLVED)
                                    .lastTransitionTime(clock.instant())
                                    .build());
                    log.debug("Populated load location {} for plugin {}", status.getLoadLocation(), pluginName);
                }
            } catch (URISyntaxException | FileNotFoundException e) {

                var condition = Condition.builder()
                        .type(ConditionType.INITIALIZED)
                        .status(ConditionStatus.FALSE)
                        .reason(ConditionReason.INVALID_PLUGIN_PATH)
                        .message("Invalid plugin path " + pluginPathAnno + " configured.")
                        .lastTransitionTime(clock.instant())
                        .build();
                status.getConditions().addAndEvictFIFO(condition);
                status.setPhase(Plugin.Phase.UNKNOWN);
                return Result.doNotRetry();
            }
        } else {

            var pluginFilename = generateFileName(plugin);
            var pluginRoot = pluginManager.getPluginsRoots().stream()
                    .filter(root -> Files.exists(root.resolve(pluginFilename)))
                    .findFirst()
                    .orElse(null);
            if (pluginRoot == null) {
                var condition = Condition.builder()
                        .type(ConditionType.INITIALIZED)
                        .status(ConditionStatus.FALSE)
                        .reason(ConditionReason.INVALID_PLUGIN_PATH)
                        .message("Cannot find plugin file " + pluginFilename + " in plugins roots.")
                        .lastTransitionTime(clock.instant())
                        .build();
                status.getConditions().addAndEvictFIFO(condition);
                status.setPhase(Plugin.Phase.UNKNOWN);
                return Result.doNotRetry();
            }
            var pluginPath = pluginRoot.resolve(pluginFilename);
            annotations.put(PLUGIN_PATH, pluginRoot.relativize(pluginPath).toString());


            var oldLoadLocation = status.getLoadLocation();
            var newLoadLocation = pluginPath.toUri();
            if (oldLoadLocation != null && !Objects.equals(oldLoadLocation, newLoadLocation)) {

                log.info(
                        "Deleting old plugin file {} for plugin {}, and new load location is {}.",
                        oldLoadLocation,
                        pluginName,
                        newLoadLocation);
                try {
                    var deleted = Files.deleteIfExists(Path.of(oldLoadLocation));
                    if (deleted) {
                        log.info("Deleted old plugin file {} for plugin {}.", oldLoadLocation, pluginName);
                    }
                } catch (IOException e) {
                    log.warn("Failed to delete old plugin file {} for plugin {}", oldLoadLocation, pluginName, e);
                } catch (FileSystemNotFoundException e) {
                    log.warn(
                            "Failed to delete old plugin file {} for plugin {}: File system not found.",
                            oldLoadLocation,
                            pluginName,
                            e);
                }
                status.setPhase(Plugin.Phase.RESOLVED);
                status.getConditions()
                        .addAndEvictFIFO(Condition.builder()
                                .type(ConditionType.INITIALIZED)
                                .status(ConditionStatus.TRUE)
                                .reason(ConditionReason.LOAD_LOCATION_RESOLVED)
                                .lastTransitionTime(clock.instant())
                                .build());
                log.debug("Populated load location {} for plugin {}", status.getLoadLocation(), pluginName);
            }
            status.setLoadLocation(newLoadLocation);
        }
        return null;
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new Plugin()).syncAllOnStart(true).build();
    }

    private void removeStartTaskIfPresent(String pluginName) {
        pluginStartTasks.computeIfPresent(pluginName, (name, disposable) -> {
            if (!disposable.isDisposed()) {
                log.info("Cancelling starting task for plugin {}.", name);
                disposable.dispose();
                log.info("Cancelled starting task for plugin {}.", name);
            }
            return null;
        });
    }

    private Result createOrUpdateReverseProxy(Plugin plugin) {
        String pluginName = plugin.getMetadata().getName();
        String reverseProxyName = buildReverseProxyName(pluginName);
        ReverseProxy reverseProxy = new ReverseProxy();
        reverseProxy.setMetadata(new Metadata());
        reverseProxy.getMetadata().setName(reverseProxyName);

        reverseProxy.getMetadata().setLabels(new HashMap<>());
        reverseProxy.getMetadata().getLabels().put(PluginConst.PLUGIN_NAME_LABEL_NAME, pluginName);

        reverseProxy.setRules(new ArrayList<>());

        String logo = plugin.getSpec().getLogo();
        if (StringUtils.isNotBlank(logo) && !PathUtils.isAbsoluteUri(logo)) {
            ReverseProxy.ReverseProxyRule logoRule =
                    new ReverseProxy.ReverseProxyRule(logo, new ReverseProxy.FileReverseProxyProvider(null, logo));
            reverseProxy.getRules().add(logoRule);
        }

        client.fetch(ReverseProxy.class, reverseProxyName)
                .ifPresentOrElse(
                        persisted -> {
                            reverseProxy
                                    .getMetadata()
                                    .setVersion(persisted.getMetadata().getVersion());
                            client.update(reverseProxy);
                        },
                        () -> client.create(reverseProxy));
        return null;
    }

    private boolean isInDevEnvironment() {
        return RuntimeMode.DEVELOPMENT.equals(pluginProperties.getRuntimeMode());
    }

    private List<String> requestToUnloadChildren(String pluginName) {

        var dependents = pluginManager.getDependents(pluginName).stream()
                .map(PluginWrapper::getPluginId)
                .toList();

        dependents.forEach(dependent -> client.fetch(Plugin.class, dependent).ifPresent(childPlugin -> {
            var labels = childPlugin.getMetadata().getLabels();
            if (labels == null) {
                labels = new HashMap<>();
                childPlugin.getMetadata().setLabels(labels);
            }
            var label = labels.get(REQUEST_TO_UNLOAD_LABEL);
            if (!pluginName.equals(label)) {
                labels.put(REQUEST_TO_UNLOAD_LABEL, pluginName);
                client.update(childPlugin);
            }
        }));
        return dependents;
    }

    private void cancelUnloadRequest(String pluginName) {

        var listOptions = ListOptions.builder()
                .labelSelector()
                .eq(REQUEST_TO_UNLOAD_LABEL, pluginName)
                .end()
                .build();
        client.listAll(Plugin.class, listOptions, Sort.unsorted()).forEach(aplugin -> {
            var labels = aplugin.getMetadata().getLabels();
            if (labels != null && labels.remove(REQUEST_TO_UNLOAD_LABEL) != null) {
                client.update(aplugin);
            }
        });
    }

    public static class ConditionType {

        public static final String INITIALIZED = "Initialized";


        public static final String PROGRESSING = "Progressing";


        public static final String READY = "Ready";
    }

    public static class ConditionReason {
        public static final String LOAD_LOCATION_RESOLVED = "LoadLocationResolved";
        public static final String INVALID_PLUGIN_PATH = "InvalidPluginPath";

        public static final String WAIT_FOR_DEPENDENCIES_STARTED = "WaitForDependenciesStarted";
        public static final String WAIT_FOR_DEPENDENCIES_LOADED = "WaitForDependenciesLoaded";

        public static final String WAIT_FOR_DEPENDENTS_DELETED = "WaitForDependentsDeleted";
        public static final String WAIT_FOR_DEPENDENTS_DISABLED = "WaitForDependentsDisabled";
        public static final String WAIT_FOR_DEPENDENTS_UNLOADED = "WaitForDependentsUnloaded";

        public static final String STARTING = "Starting";
        public static final String STARTED = "Started";
        public static final String DISABLED = "Disabled";
        public static final String SYSTEM_ERROR = "SystemError";
        public static final String REQUEST_TO_UNLOAD = "RequestToUnload";
        public static final String LOADED = "Loaded";
        public static final String START_ERROR = "StartError";
        public static final String DISABLE_ERROR = "DisableError";
        public static final String INVALID_RUNTIME_MODE = "InvalidRuntimeMode";
        public static final String PLUGIN_PATH_NOT_SET = "PluginPathNotSet";

        public static final String UNSATISFIED_REQUIRES_VERSION = "UnsatisfiedRequiresVersion";
    }
}
