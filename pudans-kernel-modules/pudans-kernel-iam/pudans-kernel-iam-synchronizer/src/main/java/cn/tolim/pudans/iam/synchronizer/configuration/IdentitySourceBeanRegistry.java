package cn.tolim.pudans.iam.synchronizer.configuration;

import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceEntity;
import cn.tolim.pudans.iam.communal.enums.TriggerType;
import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceProvider;
import cn.tolim.pudans.iam.communal.exception.IdentitySourceProviderNotSupportedException;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceRepository;
import cn.tolim.pudans.iam.identitysource.core.IdentitySource;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.event.IdentitySourceEventListener;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceEventPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncDeptPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncUserPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderConfigUtils;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderDefinition;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderRegistry;
import cn.tolim.pudans.iam.infra.scheduling.SpringSchedulerRegister;
import cn.tolim.pudans.iam.infra.trace.TraceUtils;
import cn.tolim.pudans.iam.synchronizer.provider.IdentitySourceProviderCodeReader;
import cn.tolim.pudans.iam.synchronizer.task.IdentitySourceSyncTask;
import com.cronutils.model.CronType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.util.JdkIdGenerator;
import org.springframework.util.StopWatch;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.CACHE_LOCK_KEY_PREFIX;
import static cn.tolim.pudans.iam.infra.constant.IamConstants.COLON;
import static cn.tolim.pudans.iam.synchronizer.configuration.IdentitySourceBeanUtils.getSourceBeanName;


@Slf4j
@Configuration
@AutoConfigureAfter(WebMvcAutoConfiguration.class)
public class IdentitySourceBeanRegistry implements IdentitySourceEventListener {
    private final ApplicationContext applicationContext;
    private final IdentitySourceProviderRegistry identitySourceProviderRegistry;
    private final IdentitySourceProviderCodeReader identitySourceProviderCodeReader;

    // Construct
    public IdentitySourceBeanRegistry(ApplicationContext applicationContext,
                                      IdentitySourceProviderRegistry identitySourceProviderRegistry,
                                      IdentitySourceProviderCodeReader identitySourceProviderCodeReader) {
        this.applicationContext = applicationContext;
        this.identitySourceProviderRegistry = identitySourceProviderRegistry;
        this.identitySourceProviderCodeReader = identitySourceProviderCodeReader;
        ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) applicationContext)
                .getBeanFactory();
        IdentitySourceRepository repository = beanFactory.getBean(IdentitySourceRepository.class);
        // Read raw provider codes so providers missing from the shared enum can still be resolved
        Map<String, String> providerCodes = identitySourceProviderCodeReader.findAllProviderCodes();

        Iterable<IdentitySourceEntity> list = repository.findByEnabledIsTrue();
        list.forEach(identity -> {
            if (!identity.getConfigured() || StringUtils.isBlank(identity.getBasicConfig())
                    || !identity.getEnabled()) {
                return;
            }
            String providerCode = resolveProviderCode(identity.getProvider(),
                    providerCodes.get(identity.getId()));
            // Register
            registerIdentitySourceBean(identity, providerCode, applicationContext);
            registerIdentitySourceSyncTask(identity, applicationContext);
        });
    }

    // Resolve the provider code from the shared enum or the persisted raw value
    private static String resolveProviderCode(IdentitySourceProvider provider, String rawProviderCode) {
        return provider == null ? rawProviderCode : provider.getCode();
    }

    // Register identity source bean
    private void registerIdentitySourceBean(IdentitySourceEntity entity, String providerCode,
                                            ApplicationContext applicationContext) {
        String id = entity.getId();
        ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) applicationContext)
                .getBeanFactory();
        BeanDefinitionRegistry beanDefinitionRegistry = (BeanDefinitionRegistry) ((ConfigurableApplicationContext) applicationContext)
                .getBeanFactory();
        // Destroy if it already exists
        try {
            if (ObjectUtils.isNotEmpty(beanFactory.getBean(getSourceBeanName(id)))) {
                destroyIdentitySourceBean(id, applicationContext);
            }
        } catch (NoSuchBeanDefinitionException ignored) {

        } finally {
            BeanDefinitionHolder definitionHolder = getBeanDefinitionHolder(entity, providerCode,
                    applicationContext);
            // Register bean
            BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder,
                    beanDefinitionRegistry);
        }
    }

    // Get BeanDefinitionHolder
    private BeanDefinitionHolder getBeanDefinitionHolder(@NonNull IdentitySourceEntity entity,
                                                         String providerCode,
                                                         ApplicationContext applicationContext) {
        ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
        IdentitySourceSyncUserPostProcessor identitySourceSyncUserPostProcessor = beanFactory.getBean(IdentitySourceSyncUserPostProcessor.class);
        IdentitySourceSyncDeptPostProcessor identitySourceSyncDeptPostProcessor = beanFactory.getBean(IdentitySourceSyncDeptPostProcessor.class);
        IdentitySourceEventPostProcessor identitySourceEventPostProcessor = beanFactory.getBean(IdentitySourceEventPostProcessor.class);
        IdentitySourceClient identitySourceClient;
        BeanDefinitionBuilder definitionBuilder;
        try {
            IdentitySourceProviderDefinition definition =
                    identitySourceProviderRegistry.getRequired(providerCode);
            IdentitySourceConfig config = IdentitySourceProviderConfigUtils.readConfig(definition,
                    entity.getBasicConfig());
            identitySourceClient = definition.createClient(config);
            definitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(
                    definition.getIdentitySourceType());
            return getDefinitionHolder(entity, identitySourceSyncUserPostProcessor,
                    identitySourceSyncDeptPostProcessor, identitySourceEventPostProcessor,
                    identitySourceClient, definitionBuilder, config);
        } catch (IdentitySourceProviderNotSupportedException exception) {
            log.error("未实现 [{}] 类型身份源配置", providerCode);
            throw exception;
        } catch (Exception exception) {
            log.error("获取身份源 Bean 定义异常", exception);
            throw new RuntimeException(exception.getMessage(), exception);
        }
    }

    @NotNull
    private static BeanDefinitionHolder getDefinitionHolder(IdentitySourceEntity entity,
                                                            IdentitySourceSyncUserPostProcessor identitySourceSyncUserPostProcessor,
                                                            IdentitySourceSyncDeptPostProcessor identitySourceSyncDeptPostProcessor,
                                                            IdentitySourceEventPostProcessor identitySourceEventPostProcessor,
                                                            IdentitySourceClient identitySourceClient,
                                                            BeanDefinitionBuilder definitionBuilder,
                                                            IdentitySourceConfig config) {
        definitionBuilder.addConstructorArgValue(entity.getId());
        definitionBuilder.addConstructorArgValue(entity.getName());
        definitionBuilder.addConstructorArgValue(config);
        definitionBuilder.addConstructorArgValue(identitySourceClient);
        definitionBuilder.addConstructorArgValue(identitySourceSyncUserPostProcessor);
        definitionBuilder.addConstructorArgValue(identitySourceSyncDeptPostProcessor);
        definitionBuilder.addConstructorArgValue(identitySourceEventPostProcessor);
        // Set scope to prototype
        definitionBuilder.setScope(ConfigurableBeanFactory.SCOPE_PROTOTYPE);
        return new BeanDefinitionHolder(definitionBuilder.getBeanDefinition(),
                getSourceBeanName(entity.getId()));
    }

    // Destroy identity source bean
    private static void destroyIdentitySourceBean(String id,
                                                  ApplicationContext applicationContext) {
        BeanDefinitionRegistry beanDefinitionRegistry = (BeanDefinitionRegistry) ((ConfigurableApplicationContext) applicationContext)
                .getBeanFactory();
        String beanName = getSourceBeanName(id);
        try {
            beanDefinitionRegistry.removeBeanDefinition(beanName);
        } catch (NoSuchBeanDefinitionException ignored) {
        }
    }

    // Register identity source scheduled task
    public static void registerIdentitySourceSyncTask(IdentitySourceEntity entity,
                                                      ApplicationContext applicationContext) {
        String id = entity.getId();
        String beanName = getSourceBeanName(id);
        IdentitySource<? extends IdentitySourceConfig> identitySource = (IdentitySource<? extends IdentitySourceConfig>) applicationContext
                .getBean(beanName);
        SpringSchedulerRegister schedulerRegistrarHelp = applicationContext
                .getBean(SpringSchedulerRegister.class);
        RedissonClient redissonClient = applicationContext.getBean(RedissonClient.class);
        // Register scheduled task
        String cronExpression = entity.getJobConfig().getCronExpression(CronType.SPRING);
        destroyIdentitySourceSyncTask(entity.getId(), applicationContext);
        schedulerRegistrarHelp.addCronTask(beanName, new IdentitySourceSyncTask(entity.getId(),
                entity.getName(), redissonClient, identitySource), cronExpression);
    }

    // Destroy scheduled task bean
    public static void destroyIdentitySourceSyncTask(String id,
                                                     ApplicationContext applicationContext) {
        SpringSchedulerRegister schedulerRegistrarHelp = applicationContext
                .getBean(SpringSchedulerRegister.class);
        schedulerRegistrarHelp.removeCronTask(id);
    }

    // Register
    @Override
    public void register(String id) {
        IdentitySourceRepository repository = applicationContext
                .getBean(IdentitySourceRepository.class);
        Optional<IdentitySourceEntity> optional = repository.findById(id);
        if (optional.isPresent()) {
            IdentitySourceEntity identity = optional.get();
            if (!identity.getConfigured() || StringUtils.isBlank(identity.getBasicConfig())
                    || !identity.getEnabled()) {
                return;
            }
            String providerCode = resolveProviderCode(identity.getProvider(),
                    identitySourceProviderCodeReader.findProviderCode(id));
            if (!identitySourceProviderRegistry.supports(providerCode)) {
                throw new IdentitySourceProviderNotSupportedException();
            }
            // Register
            log.info("注册身份源: {} 对象", id);
            registerIdentitySourceBean(identity, providerCode, applicationContext);
            log.info("注册身份源: {} 同步任务", id);
            registerIdentitySourceSyncTask(identity, applicationContext);
        }
    }

    // Destroy
    @Override
    public void destroy(String id) {
        // Destroy
        log.info("销毁身份源: {} 对象", id);
        destroyIdentitySourceBean(id, applicationContext);
        log.info("销毁身份源: {} 同步任务", id);
        destroyIdentitySourceSyncTask(id, applicationContext);
    }

    // Sync
    @Override
    public void sync(String id) {
        IdentitySource<? extends IdentitySourceConfig> identitySource = (IdentitySource<? extends IdentitySourceConfig>) applicationContext
                .getBean(getSourceBeanName(id));
        RedissonClient redissonClient = applicationContext.getBean(RedissonClient.class);
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        log.info("[手动任务]-同步身份源[{}]数据开始", identitySource.getName());
        TraceUtils.put(new JdkIdGenerator().generateId().toString());
        RLock lock = redissonClient.getLock(CACHE_LOCK_KEY_PREFIX + COLON + id);
        boolean tryLock = false;
        try {
            tryLock = lock.tryLock(1, TimeUnit.SECONDS);
            if (tryLock) {
                identitySource.sync(TriggerType.MANUAL);
            }
        } catch (InterruptedException ignored) {

        } finally {
            stopWatch.stop();
            if (tryLock && lock.isLocked()) {
                lock.unlock();
            }
            TraceUtils.remove();
            log.info("[手动任务]-同步身份源[{}]数据结束, 执行时长: {} ms", identitySource.getName(),
                    stopWatch.getTotalTimeMillis());
        }
    }
}