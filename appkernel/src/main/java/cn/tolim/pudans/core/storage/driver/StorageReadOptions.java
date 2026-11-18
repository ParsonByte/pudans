package cn.tolim.pudans.core.storage.driver;

public record StorageReadOptions(ByteRange range) {

    public static StorageReadOptions full() {
        return new StorageReadOptions(null);
    }
}
