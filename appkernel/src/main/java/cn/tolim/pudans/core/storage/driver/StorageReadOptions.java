package cn.tolim.pudans.core.storage.driver;

public record StorageReadOptions(ByteRange range) {

    // 返回读取完整对象的选项。
    public static StorageReadOptions full() {
        return new StorageReadOptions(null);
    }
}
