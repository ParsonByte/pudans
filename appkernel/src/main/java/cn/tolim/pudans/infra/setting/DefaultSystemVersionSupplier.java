package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.infra.contract.SystemVersionSupplier;
import com.github.zafarkhaja.semver.Version;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;


@Component
public class DefaultSystemVersionSupplier implements SystemVersionSupplier {
    private static final String DEFAULT_VERSION = "0.0.0";

    private final ObjectProvider<BuildProperties> buildProperties;

    // 注入构建信息提供者，缺失时回退默认版本。
    public DefaultSystemVersionSupplier(ObjectProvider<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    // 读取构建版本，缺失或非法时回退默认值。
    @Override
    public Version get() {
        var properties = buildProperties.getIfUnique();
        if (properties == null) {
            return Version.parse(DEFAULT_VERSION);
        }
        var projectVersion = Objects.toString(properties.getVersion(), DEFAULT_VERSION);
        return Version.parse(projectVersion);
    }
}
