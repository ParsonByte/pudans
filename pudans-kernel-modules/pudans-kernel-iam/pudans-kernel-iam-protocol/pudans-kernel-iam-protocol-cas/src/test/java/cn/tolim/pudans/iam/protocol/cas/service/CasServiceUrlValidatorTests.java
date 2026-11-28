package cn.tolim.pudans.iam.protocol.cas.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;


class CasServiceUrlValidatorTests {

    @Test
    void acceptsExactAndPrefixMatches() {
        Set<String> allowed = Set.of("https://app.test/callback");

        assertThat(CasServiceUrlValidator.isAllowed(allowed, "https://app.test/callback")).isTrue();
        assertThat(CasServiceUrlValidator.isAllowed(allowed,
                "https://app.test/callback/deep")).isTrue();
        assertThat(CasServiceUrlValidator.isAllowed(allowed,
                "https://app.test/callback?x=1")).isTrue();
    }

    @Test
    void rejectsDifferentHostSchemePortAndPath() {
        Set<String> allowed = Set.of("https://app.test/callback");

        assertThat(CasServiceUrlValidator.isAllowed(allowed, "https://evil.test/callback"))
                .isFalse();
        assertThat(CasServiceUrlValidator.isAllowed(allowed, "http://app.test/callback"))
                .isFalse();
        assertThat(CasServiceUrlValidator.isAllowed(allowed, "https://app.test:8443/callback"))
                .isFalse();
        assertThat(CasServiceUrlValidator.isAllowed(allowed, "https://app.test/other")).isFalse();
    }

    @Test
    void rejectsMissingNonHttpAndBlankValues() {
        assertThat(CasServiceUrlValidator.isAllowed(Set.of(), "https://app.test")).isFalse();
        assertThat(CasServiceUrlValidator.isAllowed(Set.of("https://app.test"), " ")).isFalse();
        assertThat(CasServiceUrlValidator.isAllowed(Set.of("https://app.test"),
                "javascript:alert(1)")).isFalse();
    }
}