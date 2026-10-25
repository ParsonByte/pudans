package cn.tolim.pudans.plugin;

import cn.tolim.pudans.contract.audit.PluginAuditService;
import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.BackupRootGetter;
import cn.tolim.pudans.contract.infra.ExternalLinkProcessor;
import cn.tolim.pudans.contract.infra.ExternalUrlSupplier;
import cn.tolim.pudans.contract.infra.SystemInfoGetter;
import cn.tolim.pudans.contract.plugin.PluginsRootGetter;
import cn.tolim.pudans.contract.plugin.extensionpoint.ExtensionGetter;
import cn.tolim.pudans.contract.security.LoginHandlerEnhancer;
import cn.tolim.pudans.contract.security.authentication.CryptoService;
import cn.tolim.pudans.extension.DefaultSchemeManager;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.ServerRequestCache;


public enum SharedApplicationContextFactory {
    ;

    public static ApplicationContext create(ApplicationContext rootContext) {

        var sharedContext = new GenericApplicationContext();
        sharedContext.registerShutdownHook();

        var beanFactory = sharedContext.getBeanFactory();


        var extensionClient = rootContext.getBean(ExtensionClient.class);
        var reactiveExtensionClient = rootContext.getBean(ReactiveExtensionClient.class);
        beanFactory.registerSingleton("extensionClient", extensionClient);
        beanFactory.registerSingleton("reactiveExtensionClient", reactiveExtensionClient);

        DefaultSchemeManager defaultSchemeManager = rootContext.getBean(DefaultSchemeManager.class);
        beanFactory.registerSingleton("schemeManager", defaultSchemeManager);
        beanFactory.registerSingleton("externalUrlSupplier", rootContext.getBean(ExternalUrlSupplier.class));
        beanFactory.registerSingleton(
                "serverSecurityContextRepository", rootContext.getBean(ServerSecurityContextRepository.class));
        beanFactory.registerSingleton("backupRootGetter", rootContext.getBean(BackupRootGetter.class));
        beanFactory.registerSingleton("externalLinkProcessor", rootContext.getBean(ExternalLinkProcessor.class));
        beanFactory.registerSingleton("cacheManager", rootContext.getBean(CacheManager.class));
        beanFactory.registerSingleton("loginHandlerEnhancer", rootContext.getBean(LoginHandlerEnhancer.class));
        rootContext
                .getBeanProvider(PluginsRootGetter.class)
                .ifUnique(pluginsRootGetter -> beanFactory.registerSingleton("pluginsRootGetter", pluginsRootGetter));
        beanFactory.registerSingleton("extensionGetter", rootContext.getBean(ExtensionGetter.class));
        rootContext
                .getBeanProvider(PluginAuditService.class)
                .ifUnique(pluginAuditService ->
                        beanFactory.registerSingleton("pluginAuditService", pluginAuditService));
        rootContext
                .getBeanProvider(CryptoService.class)
                .ifUnique(cryptoService -> beanFactory.registerSingleton("cryptoService", cryptoService));
        rootContext
                .getBeanProvider(RateLimiterRegistry.class)
                .ifUnique(rateLimiterRegistry ->
                        beanFactory.registerSingleton("rateLimiterRegistry", rateLimiterRegistry));


        rootContext
                .getBeanProvider(ServerRequestCache.class)
                .ifUnique(
                        serverRequestCache -> beanFactory.registerSingleton("serverRequestCache", serverRequestCache));
        rootContext
                .getBeanProvider(UserService.class)
                .ifUnique(userService -> beanFactory.registerSingleton("userService", userService));
        rootContext
                .getBeanProvider(RoleService.class)
                .ifUnique(roleService -> beanFactory.registerSingleton("roleService", roleService));
        rootContext
                .getBeanProvider(ReactiveUserDetailsService.class)
                .ifUnique(
                        userDetailsService -> beanFactory.registerSingleton("userDetailsService", userDetailsService));
        rootContext
                .getBeanProvider(SystemInfoGetter.class)
                .ifUnique(systemInfoGetter -> beanFactory.registerSingleton("systemInfoGetter", systemInfoGetter));


        sharedContext.refresh();
        return sharedContext;
    }
}
