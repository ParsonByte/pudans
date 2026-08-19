package cn.tolim.pudans.contract.plugin.extensionpoint;

import java.util.List;
import org.pf4j.ExtensionPoint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ExtensionGetter {


    <T extends ExtensionPoint> Mono<T> getEnabledExtension(Class<T> extensionPoint);


    <T extends ExtensionPoint> Flux<T> getEnabledExtensions(Class<T> extensionPoint);


    <T extends ExtensionPoint> Flux<T> getExtensions(Class<T> extensionPointClass);


    <T extends ExtensionPoint> List<T> getExtensionList(Class<T> extensionPointClass);
}
