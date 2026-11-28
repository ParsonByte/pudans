package cn.tolim.pudans.apidocs;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

// Dynamic proxy factory for interfaces that must stay inert during doc generation.
// Object-returning methods produce nested proxies, primitives return zero values, and String
// returns an empty string, which is enough for startup wiring without external services.
final class InertProxy {

    private InertProxy() {
    }

    @SuppressWarnings("unchecked")
    static <T> T create(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                InertProxy::invoke);
    }

    private static Object invoke(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "toString" -> "inert-proxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> defaultValue(method.getReturnType());
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            if (type == String.class) {
                return "";
            }
            if (type.isInterface()) {
                return create(type);
            }
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }

}