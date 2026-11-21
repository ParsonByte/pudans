package cn.tolim.pudans.extension.contract;

@FunctionalInterface
public interface ExtensionMatcher {

    // 判断扩展是否命中当前选择条件。
    boolean match(Extension extension);
}
