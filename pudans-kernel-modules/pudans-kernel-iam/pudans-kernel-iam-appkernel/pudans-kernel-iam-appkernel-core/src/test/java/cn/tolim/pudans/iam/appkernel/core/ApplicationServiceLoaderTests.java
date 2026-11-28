package cn.tolim.pudans.iam.appkernel.core;

import cn.tolim.pudans.iam.appkernel.core.exception.AppNotExistException;
import cn.tolim.pudans.iam.appkernel.core.exception.AppTemplateNotExistException;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationServiceLoaderTests {

    private ApplicationContext applicationContext;
    private AppRepository appRepository;
    private ApplicationService jwtService;
    private ApplicationServiceLoader loader;

    @BeforeEach
    void setUp() {
        applicationContext = mock(ApplicationContext.class);
        appRepository = mock(AppRepository.class);
        jwtService = mock(ApplicationService.class);
        when(jwtService.getCode()).thenReturn("jwt");
        Map<String, ApplicationService> beanMap = new HashMap<>();
        beanMap.put("jwtService", jwtService);
        when(applicationContext.getBeansOfType(ApplicationService.class)).thenReturn(beanMap);
        when(applicationContext.getBean(AppRepository.class)).thenReturn(appRepository);
        loader = new ApplicationServiceLoader();
        loader.setApplicationContext(applicationContext);
    }

    @Test
    void shouldResolveApplicationServiceByCode() {
        assertSame(jwtService, loader.getApplicationService("jwt"));
        assertSame(jwtService, loader.getApplicationService("jwt"));
        assertEquals(List.of(jwtService), loader.getApplicationServiceList());
    }

    @Test
    void shouldRejectUnknownApplicationTemplate() {
        assertThrows(AppTemplateNotExistException.class,
                () -> loader.getApplicationService("missing"));
    }

    @Test
    void shouldResolveApplicationServiceByApplicationId() {
        AppEntity app = new AppEntity();
        app.setTemplate("jwt");
        when(appRepository.findById("app-1")).thenReturn(Optional.of(app));

        assertSame(jwtService, loader.getApplicationServiceByAppId("app-1"));
        assertThrows(AppNotExistException.class,
                () -> loader.getApplicationServiceByAppId("missing"));
    }

    @Test
    void shouldResolveApplicationServiceByApplicationCode() {
        AppEntity app = new AppEntity();
        app.setTemplate("jwt");
        when(appRepository.findByCode("app-code")).thenReturn(Optional.of(app));

        assertSame(jwtService, loader.getApplicationServiceByAppCode("app-code"));
        assertThrows(AppNotExistException.class,
                () -> loader.getApplicationServiceByAppCode("missing"));
    }

    @Test
    void shouldAddApplicationServicesByBeanName() {
        ApplicationService extraService = mock(ApplicationService.class);
        when(extraService.getCode()).thenReturn("extra");
        when(applicationContext.getBean("extraService", ApplicationService.class))
                .thenReturn(extraService);

        loader.addApplicationService(List.of("extraService"));

        assertSame(extraService, loader.getApplicationService("extra"));
        verify(applicationContext).getBean("extraService", ApplicationService.class);
    }
}