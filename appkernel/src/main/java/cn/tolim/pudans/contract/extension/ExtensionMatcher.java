package cn.tolim.pudans.contract.extension;

@FunctionalInterface
public interface ExtensionMatcher {

    boolean match(Extension extension);
}
