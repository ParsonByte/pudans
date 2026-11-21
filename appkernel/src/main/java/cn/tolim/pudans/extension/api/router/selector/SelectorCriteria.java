package cn.tolim.pudans.extension.api.router.selector;

import java.util.Set;

public record SelectorCriteria(String key, Operator operator, Set<String> values) {}
