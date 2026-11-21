package cn.tolim.pudans.core.storage.driver;

public record ByteRange(long start, long end) {

    // 返回闭区间表示的字节数。
    public long length() {
        return end - start + 1;
    }
}
