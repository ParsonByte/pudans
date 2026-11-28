package cn.tolim.pudans.iam.appkernel.core;

import cn.tolim.pudans.iam.appkernel.core.exception.AppNotExistException;
import cn.tolim.pudans.iam.appkernel.core.exception.AppTemplateNotExistException;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

import java.util.*;


@Configuration
public class ApplicationServiceLoader implements ApplicationContextAware {

    private final Logger logger = LoggerFactory
            .getLogger(ApplicationServiceLoader.class);
    // key: code, value: templateImpl
    private final Map<String, ApplicationService> applicationServiceMap = new HashMap<>(16);
    // Store implementation class names and their classes
    private Map<String, ApplicationService> loadMap = new HashMap<>(16);
    private ApplicationContext applicationContext;

    // Set the ApplicationContext that this object runs in.
    // Normally this call will be used to initialize the object.
    // Invoked after population of normal bean properties but before an init callback such
    // as InitializingBean#afterPropertiesSet()
    // or a custom init-method. Invoked after ResourceLoaderAware#setResourceLoader and
    // ApplicationEventPublisherAware#setApplicationEventPublisher.
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        loadMap = applicationContext.getBeansOfType(ApplicationService.class);
    }

    // Get application list
    public List<ApplicationService> getApplicationServiceList() {
        return loadMap.values().stream().toList();
    }

    // Get application by code
    public ApplicationService getApplicationService(String code) {
        ApplicationService impl = applicationServiceMap.get(code);
        if (!Objects.isNull(impl)) {
            return impl;
        }
        for (ApplicationService service : getApplicationServiceList()) {
            if (code.equals(service.getCode())) {
                applicationServiceMap.put(code, service);
                return service;
            }
        }
        throw new AppTemplateNotExistException();
    }

    // Get application service by application code
    public ApplicationService getApplicationServiceByAppId(String appId) {
        AppRepository repository = applicationContext.getBean(AppRepository.class);
        Optional<AppEntity> optional = repository.findById(appId);
        if (optional.isEmpty()) {
            throw new AppNotExistException();
        }
        AppEntity app = optional.get();
        return getApplicationService(app.getTemplate());
    }

    // Get application service by application code
    public ApplicationService getApplicationServiceByAppCode(String appCode) {
        AppRepository repository = applicationContext.getBean(AppRepository.class);
        Optional<AppEntity> optional = repository.findByCode(appCode);
        if (optional.isEmpty()) {
            throw new AppNotExistException();
        }
        AppEntity app = optional.get();
        return getApplicationService(app.getTemplate());
    }

    public void addApplicationService(List<String> beanNameList) {
        Map<String, ApplicationService> applicationServiceMap = new HashMap<>(16);
        for (String beanName : beanNameList) {
            applicationServiceMap.put(beanName,
                    applicationContext.getBean(beanName, ApplicationService.class));
        }
        loadMap.putAll(applicationServiceMap);
    }
}
