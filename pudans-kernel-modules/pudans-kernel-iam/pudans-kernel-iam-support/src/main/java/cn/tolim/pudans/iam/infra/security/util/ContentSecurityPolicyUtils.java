package cn.tolim.pudans.iam.infra.security.util;

import cn.tolim.pudans.iam.infra.exception.PudansException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class ContentSecurityPolicyUtils {

    private static final String POLICY_CLASS = "com.shapesecurity.salvation2.Policy";
    private static final String ERROR_CONSUMER_CLASS = POLICY_CLASS
            + "$PolicyErrorConsumer";

    private ContentSecurityPolicyUtils() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T parse(String serializedPolicy) {
        ClassLoader classLoader = ContentSecurityPolicyUtils.class.getClassLoader();
        try {
            Class<?> policyClass = Class.forName(POLICY_CLASS, true, classLoader);
            Class<?> consumerClass = Class.forName(ERROR_CONSUMER_CLASS, true, classLoader);
            Object errorConsumer = Proxy.newProxyInstance(classLoader,
                    new Class<?>[]{consumerClass}, (proxy, method, arguments) -> {
                        if ("add".equals(method.getName()) && arguments != null
                                && arguments.length > 1) {
                            throw new PudansException(String.valueOf(arguments[1]));
                        }
                        return defaultProxyValue(proxy, method, arguments);
                    });
            Method parseMethod = policyClass.getMethod("parseSerializedCSP", String.class,
                    consumerClass);
            return (T) parseMethod.invoke(null, serializedPolicy, errorConsumer);
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            throw new PudansException("内容安全策略解析器不可用", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new PudansException("无法解析内容安全策略", cause);
        } catch (IllegalAccessException exception) {
            throw new PudansException("无法访问内容安全策略解析器",
                    exception);
        }
    }

    private static Object defaultProxyValue(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "toString" -> "ContentSecurityPolicyErrorConsumer";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (arguments == null || arguments.length == 0 ? null
                    : arguments[0]);
            default -> null;
        };
    }
}
