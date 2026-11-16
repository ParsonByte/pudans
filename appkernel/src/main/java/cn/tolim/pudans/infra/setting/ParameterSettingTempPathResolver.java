package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.infra.properties.PudansProperties;
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

    private final ParameterSettingGroupService parameterSettingGroupService;

    private final PudansProperties pudansProperties;

    public Mono<Path> resolve() {
        return parameterSettingGroupService
                .getServer()
                .map(server -> resolve(server.getTempPath()))
                .defaultIfEmpty(resolve(DEFAULT_TEMP_PATH));
    }

    public Path resolve(String tempPath) {
        var relative = StringUtils.hasText(tempPath) ? tempPath : DEFAULT_TEMP_PATH;
        var workDir = pudansProperties.getWorkDir().toAbsolutePath().normalize();
        var resolved = workDir.resolve(relative).normalize();
        if (!resolved.startsWith(workDir)) {
            log.warn("Ignoring parameter temp path outside the data directory: {}", tempPath);
            return workDir.resolve(DEFAULT_TEMP_PATH);
        }
        return resolved;
    }
}
