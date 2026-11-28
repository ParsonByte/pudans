package cn.tolim.pudans.iam.synchronizer.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentitySourceBeanUtilsTests {

    @Test
    void shouldBuildStableMd5BeanName() {
        String first = IdentitySourceBeanUtils.getSourceBeanName("source-1");
        String second = IdentitySourceBeanUtils.getSourceBeanName("source-1");
        String other = IdentitySourceBeanUtils.getSourceBeanName("source-2");

        assertEquals(first, second);
        assertFalse(first.equals(other));
        assertTrue(first.startsWith("identitySourceBean_"));
        assertEquals("identitySourceBean_".length() + 32, first.length());
    }
}