package cn.tolim.pudans.core.storage.driver;

import java.time.Instant;

public record StorageTestResult(boolean success, String message, Instant testedAt) {}
