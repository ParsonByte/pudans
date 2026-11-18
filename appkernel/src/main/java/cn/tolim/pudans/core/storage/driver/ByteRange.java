package cn.tolim.pudans.core.storage.driver;

public record ByteRange(long start, long end) {

    public long length() {
        return end - start + 1;
    }
}
