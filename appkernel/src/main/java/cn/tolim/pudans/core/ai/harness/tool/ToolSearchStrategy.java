package cn.tolim.pudans.core.ai.harness.tool;

@FunctionalInterface
public interface ToolSearchStrategy {

    ToolSearchResult search(ToolSearchRequest request);
}