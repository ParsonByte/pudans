package cn.tolim.pudans.iam.identitysource.core.client;

import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import lombok.Getter;
import org.springframework.util.Assert;


@Getter
public abstract class AbstractIdentitySourceClient<T extends IdentitySourceConfig>
        implements IdentitySourceClient {

    public static String ACCESS_KEY = "ACCESS_KEY";
    // Configuration
    private final T config;

    protected AbstractIdentitySourceClient(T config) {
        Assert.notNull(config, "IdentityProviderConfig 不能为空");
        this.config = config;
    }
}
