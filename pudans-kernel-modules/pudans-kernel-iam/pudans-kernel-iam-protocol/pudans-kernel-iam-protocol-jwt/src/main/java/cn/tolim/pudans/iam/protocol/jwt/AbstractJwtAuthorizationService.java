
package cn.tolim.pudans.iam.protocol.jwt;

import cn.tolim.pudans.iam.appkernel.core.AppAccount;
import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.appkernel.jwt.JwtApplicationService;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.protocol.core.LoginAccount;
import cn.tolim.pudans.iam.protocol.jwt.client.JwtRegisteredClient;
import org.springframework.util.Assert;


public abstract class AbstractJwtAuthorizationService implements JwtAuthorizationService {
    private final ApplicationServiceLoader applicationServiceLoader;

    protected AbstractJwtAuthorizationService(ApplicationServiceLoader applicationServiceLoader) {
        Assert.notNull(applicationServiceLoader, "applicationServiceLoader 不能为空");
        this.applicationServiceLoader = applicationServiceLoader;
    }

    @Override
    public LoginAccount getDefaultLoginAccount(JwtRegisteredClient client, UserDetails user) {
        JwtApplicationService applicationService = (JwtApplicationService) applicationServiceLoader
                .getApplicationServiceByAppCode(client.getCode());
        AppAccount appAccount = applicationService.getDefaultAppAccount(client.getId(),
                user.getId());
        return LoginAccount.builder().appId(appAccount.getAppId()).userId(appAccount.getUserId())
                .username(appAccount.getAccount()).password(appAccount.getPassword())
                .isDefault(appAccount.getDefaulted()).build();
    }
}
