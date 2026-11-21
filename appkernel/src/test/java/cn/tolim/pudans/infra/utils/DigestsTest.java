package cn.tolim.pudans.infra.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DigestsTest {

    // 校验普通 SHA-256 十六进制输出。
    @Test
    void hashesTextWithSha256() {
        assertThat(Digests.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    // 校验 HMAC-SHA256 与已知向量一致。
    @Test
    void signsTextWithHmacSha256() {
        assertThat(Digests.hmacSha256Hex("key", "The quick brown fox jumps over the lazy dog"))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    // 空密钥必须退化为普通 SHA-256，兼容旧令牌。
    @Test
    void fallsBackToSha256WithoutKey() {
        assertThat(Digests.hmacSha256Hex("", "abc")).isEqualTo(Digests.sha256Hex("abc"));
    }

    // 常量时间比较要正确处理相同、不同与空值。
    @Test
    void comparesDigestsInConstantTime() {
        assertThat(Digests.constantTimeEquals("abc", "abc")).isTrue();
        assertThat(Digests.constantTimeEquals("abc", "abd")).isFalse();
        assertThat(Digests.constantTimeEquals(null, "abc")).isFalse();
        assertThat(Digests.constantTimeEquals("abc", null)).isFalse();
    }
}
