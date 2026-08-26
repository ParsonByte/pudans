package cn.tolim.pudans.infra;

import cn.tolim.pudans.contract.infra.BackupRootGetter;
import cn.tolim.pudans.infra.properties.PudansProperties;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class DefaultBackupRootGetter implements BackupRootGetter {

    private final PudansProperties pudansProperties;

    public DefaultBackupRootGetter(PudansProperties pudansProperties) {
        this.pudansProperties = pudansProperties;
    }

    @Override
    public Path get() {
        return pudansProperties.getWorkDir().resolve("backups");
    }
}
