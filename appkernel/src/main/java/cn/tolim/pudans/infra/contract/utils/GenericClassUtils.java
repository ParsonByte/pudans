package cn.tolim.pudans.infra.contract.utils;

import java.util.function.Supplier;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;

public enum GenericClassUtils {
    ;


    public static <T> Class<?> generateConcreteClass(Class<?> rawClass, Class<T> parameterType) {
        return generateConcreteClass(rawClass, parameterType, () -> parameterType.getName() + rawClass.getSimpleName());
    }


    public static <T> Class<?> generateConcreteClass(
            Class<?> rawClass, Class<T> parameterType, Supplier<String> nameGenerator) {
        var concreteType = TypeDescription.Generic.Builder.parameterizedType(rawClass, parameterType)
                .build();
        try (var unloaded =
                     new ByteBuddy().subclass(concreteType).name(nameGenerator.get()).make()) {
            return unloaded.load(parameterType.getClassLoader()).getLoaded();
        }
    }
}
