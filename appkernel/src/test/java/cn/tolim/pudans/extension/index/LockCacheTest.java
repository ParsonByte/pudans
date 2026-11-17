package cn.tolim.pudans.extension.index;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LockCacheTest {

    @Test
    void releasesTheEntryAfterBalancedUse() {
        var cache = new LockCache();

        try (var ignored = cache.acquire("alice")) {
            assertThat(cache.size()).isEqualTo(1);
        }

        assertThat(cache.size()).isZero();
    }

    @Test
    void keepsTheEntryWhileANestedHolderIsActive() {
        var cache = new LockCache();

        try (var outer = cache.acquire("alice")) {
            try (var inner = cache.acquire("alice")) {
                assertThat(cache.size()).isEqualTo(1);
            }
            assertThat(cache.size()).isEqualTo(1);
        }

        assertThat(cache.size()).isZero();
    }
}
