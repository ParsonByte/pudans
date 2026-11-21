package cn.tolim.pudans.infra.contract.utils;

import java.util.function.Supplier;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;

public enum GenericClassUtils {
    ;


    // 使用参数类型名生成具体子类名称。
    public static <T> Class<?> generateConcreteClass(Class<?> rawClass, Class<T> parameterType) {
        return generateConcreteClass(rawClass, parameterType, () -> parameterType.getName() + rawClass.getSimpleName());
    }


    // 借助 Byte Buddy 生成带泛型绑定的子类。
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
