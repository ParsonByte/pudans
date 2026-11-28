package cn.tolim.pudans.iam.protocol.cas.ticket;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.protocol.cas.application.CasApplicationService;
import cn.tolim.pudans.iam.protocol.cas.model.CasProtocolConfig;
import org.springframework.util.Assert;


public abstract class AbstractCasTicketService implements CasTicketService {

    private final ApplicationServiceLoader applicationServiceLoader;

    protected AbstractCasTicketService(ApplicationServiceLoader applicationServiceLoader) {
        Assert.notNull(applicationServiceLoader, "applicationServiceLoader must not be null");
        this.applicationServiceLoader = applicationServiceLoader;
    }

    // Resolve the CAS protocol configuration for an application.
    protected CasProtocolConfig getProtocolConfig(String appCode) {
        CasApplicationService applicationService = (CasApplicationService)
                applicationServiceLoader.getApplicationServiceByAppCode(appCode);
        return applicationService.getProtocolConfig(appCode);
    }
}