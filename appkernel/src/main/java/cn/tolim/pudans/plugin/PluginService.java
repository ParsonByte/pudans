package cn.tolim.pudans.plugin;

import cn.tolim.pudans.contract.core.extension.Plugin;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import org.pf4j.PluginWrapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PluginService {

    Mono<Void> installPresetPlugins();


    Mono<Plugin> install(Path path);

    Mono<Plugin> upgrade(String name, Path path);


    Mono<Plugin> reload(String name);


    Flux<DataBuffer> uglifyJsBundle();


    Flux<DataBuffer> uglifyCssBundle();


    Mono<String> generateBundleVersion();


    Mono<Resource> getJsBundle(String version);


    Mono<Resource> getCssBundle(String version);


    Mono<Plugin> changeState(String pluginName, boolean requestToEnable, boolean wait);


    List<String> getRequiredDependencies(Plugin plugin, Predicate<PluginWrapper> predicate);


    Flux<String> getStartedPluginNames();
}
