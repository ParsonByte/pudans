package cn.tolim.pudans.extension.api;

@FunctionalInterface
public interface ExtensionMatcher {

    boolean match(Extension extension);
}
