package cn.tolim.pudans.repository.contract;

public record StorageProviderResponse(String provider, boolean configured, boolean defaultProvider) {
}