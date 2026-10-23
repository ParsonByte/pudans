package cn.tolim.pudans.plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.pf4j.RuntimeMode;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties(prefix = "pudans.plugin")
public class PluginProperties {
    public static final String GRADLE_LIBS_DIR = "build/libs";


    private boolean autoStartPlugin = true;


    private List<Path> fixedPluginPath = new ArrayList<>();


    private String[] disabledPlugins = new String[0];


    private String[] enabledPlugins = new String[0];


    private boolean exactVersionAllowed = false;


    private List<String> classesDirectories = new ArrayList<>();


    private List<String> libDirectories = new ArrayList<>(List.of(GRADLE_LIBS_DIR));


    private RuntimeMode runtimeMode = RuntimeMode.DEPLOYMENT;
}
