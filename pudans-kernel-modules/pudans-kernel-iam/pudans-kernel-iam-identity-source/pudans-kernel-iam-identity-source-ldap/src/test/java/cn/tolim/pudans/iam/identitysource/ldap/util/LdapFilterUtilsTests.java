package cn.tolim.pudans.iam.identitysource.ldap.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


class LdapFilterUtilsTests {

    @Test
    void escapeShouldEncodeSpecialCharacters() {
        assertEquals("a\\2ab\\28c\\29d\\5ce", LdapFilterUtils.escape("a*b(c)d\\e"));
    }

    @Test
    void escapeShouldReturnEmptyForNull() {
        assertEquals("", LdapFilterUtils.escape(null));
    }

    @Test
    void equalityShouldBuildAssertionWithEscapedValue() {
        assertEquals("(uid=ali\\2ace)", LdapFilterUtils.equality("uid", "ali*ce"));
    }

    @Test
    void andShouldJoinOnlyNonBlankFilters() {
        assertEquals("(&(a=1)(b=2))",
                LdapFilterUtils.and("(a=1)", "", null, "(b=2)"));
    }

    @Test
    void andShouldFallBackToMatchAllWhenEmpty() {
        assertEquals("(objectClass=*)", LdapFilterUtils.and("", null));
    }

    @Test
    void orShouldJoinOnlyNonBlankFilters() {
        assertEquals("(|(a=1)(b=2))", LdapFilterUtils.or("(a=1)", "(b=2)"));
    }

    @Test
    void normalizeShouldWrapBareFilter() {
        assertEquals("(uid=alice)", LdapFilterUtils.normalize("uid=alice"));
        assertEquals("(uid=alice)", LdapFilterUtils.normalize("(uid=alice)"));
    }
}