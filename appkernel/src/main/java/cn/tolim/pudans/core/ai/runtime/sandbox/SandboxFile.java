package cn.tolim.pudans.core.ai.runtime.sandbox;

public record SandboxFile(String path, String encoding, String content, long size) {

    public SandboxFile {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path 不能为空");
        }
        if (encoding == null || encoding.isBlank()) {
            throw new IllegalArgumentException("encoding 不能为空");
        }
        content = content == null ? "" : content;
    }
}