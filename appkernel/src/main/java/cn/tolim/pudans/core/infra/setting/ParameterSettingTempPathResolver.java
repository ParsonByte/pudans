package cn.tolim.pudans.core.infra.setting;

import cn.tolim.pudans.core.infra.properties.PudansProperties;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParameterSettingTempPathResolver {

    private static final String DEFAULT_TEMP_PATH = "tmp";

    private final ParameterGroups parameterGroups;

    private final PudansProperties pudansProperties;

    public Mono<Path> resolve() {
        return parameterGroups
                .getServer()
                .map(server -> resolve(server.getTempPath()))
                .defaultIfEmpty(resolve(DEFAULT_TEMP_PATH));
    }

    public Path resolve(String tempPath) {
        var relative = StringUtils.hasText(tempPath) ? tempPath : DEFAULT_TEMP_PATH;
        var workDir = pudansProperties.getWorkDir().toAbsolutePath().normalize();
        var resolved = workDir.resolve(relative).normalize();
        if (!resolved.startsWith(workDir)
                || resolved.equals(workDir)
                || resolved.startsWith(workDir.resolve("keys"))) {
            log.warn("忽略数据目录之外的参数临时路径：{}", tempPath);
            return workDir.resolve(DEFAULT_TEMP_PATH);
        }
        return resolved;
    }
}
