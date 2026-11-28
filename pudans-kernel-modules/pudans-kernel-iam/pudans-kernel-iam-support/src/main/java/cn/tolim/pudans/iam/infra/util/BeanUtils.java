package cn.tolim.pudans.iam.infra.util;

import org.springframework.beans.BeansException;

import java.util.HashSet;
import java.util.Set;

public class BeanUtils extends org.springframework.beans.BeanUtils {
    public static void merge(Object source, Object target, String... ignoreProperties)
            throws BeansException {
        merge(source, target, null, ignoreProperties);
    }

    public static void merge(Object source, Object target) throws BeansException {
        merge(source, target, null, (String[]) null);
    }

    public static void merge(Object source, Object target, Class<?> editable,
                             String... ignoreProperties) throws BeansException {
        if (source == null || target == null) {
            return;
        }
        Set<String> ignored = new HashSet<>();
        if (ignoreProperties != null) {
            ignored.addAll(java.util.Arrays.asList(ignoreProperties));
        }
        String[] ignoredProperties = ignored.toArray(String[]::new);
        if (editable == null) {
            copyProperties(source, target, ignoredProperties);
        } else if (ignoredProperties.length == 0) {
            copyProperties(source, target, editable);
        } else {
            copyProperties(source, target, ignoredProperties);
        }
    }
}
