package cn.tolim.pudans.plugin;

import cn.tolim.pudans.contract.plugin.SpringPlugin;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.pf4j.ExtensionFactory;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.context.ApplicationContext;


@Slf4j
@RequiredArgsConstructor
public class SpringExtensionFactory implements ExtensionFactory {


    protected final PluginManager pluginManager;

    @Override
    @Nullable
    public <T> T create(Class<T> extensionClass) {
        return getPluginApplicationContextBy(extensionClass)
                .map(context -> context.getBean(extensionClass))
                .orElseGet(() -> createWithoutSpring(extensionClass));
    }


    @SuppressWarnings("unchecked")
    protected <T> T createWithoutSpring(final Class<T> extensionClass) throws IllegalArgumentException {
        final Constructor<?> constructor = getPublicConstructorWithShortestParameterList(extensionClass)

                .orElseThrow(() -> new IllegalArgumentException(
                        "Extension class '" + nameOf(extensionClass) + "' must have at least one public constructor."));
        try {
            if (log.isTraceEnabled()) {
                log.trace("Instantiate '" + nameOf(extensionClass) + "' by calling '" + constructor
                        + "'with standard Java reflection.");
            }


            return (T) constructor.newInstance(nullParameters(constructor));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {


            log.error(ex.getMessage(), ex);
            throw new RuntimeException(
                    "Most likely this exception is thrown because the called constructor ("
                            + constructor + ")"
                            + " cannot handle 'null' parameters. Original message was: "
                            + ex.getMessage(),
                    ex);
        }
    }

    private Optional<Constructor<?>> getPublicConstructorWithShortestParameterList(final Class<?> extensionClass) {
        return Stream.of(extensionClass.getConstructors()).min(Comparator.comparing(Constructor::getParameterCount));
    }

    private Object[] nullParameters(final Constructor<?> constructor) {
        return new Object[constructor.getParameterCount()];
    }

    protected <T> Optional<ApplicationContext> getPluginApplicationContextBy(final Class<T> extensionClass) {
        return Optional.ofNullable(this.pluginManager.whichPlugin(extensionClass))
                .map(PluginWrapper::getPlugin)
                .filter(SpringPlugin.class::isInstance)
                .map(plugin -> (SpringPlugin) plugin)
                .map(SpringPlugin::getApplicationContext);
    }

    private <T> String nameOf(final Class<T> clazz) {
        return clazz.getName();
    }
}
