package cn.tolim.pudans.extension.contract;

@FunctionalInterface
public interface ExtensionMatcher {

    boolean match(Extension extension);
}
