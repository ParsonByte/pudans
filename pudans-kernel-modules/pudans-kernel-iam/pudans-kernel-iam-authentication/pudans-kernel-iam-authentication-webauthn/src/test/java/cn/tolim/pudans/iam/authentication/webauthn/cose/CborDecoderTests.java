package cn.tolim.pudans.iam.authentication.webauthn.cose;

import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnVerificationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CborDecoderTests {

    @Test
    void decodesUnsignedAndNegativeIntegers() {
        assertEquals(0L, CborDecoder.decode(new byte[]{0x00}));
        assertEquals(23L, CborDecoder.decode(new byte[]{0x17}));
        assertEquals(24L, CborDecoder.decode(new byte[]{0x18, 0x18}));
        assertEquals(-1L, CborDecoder.decode(new byte[]{(byte) 0x20}));
        assertEquals(-7L, CborDecoder.decode(new byte[]{0x26}));
        assertEquals(-8L, CborDecoder.decode(new byte[]{0x27}));
    }

    @Test
    void decodesByteStringsAndText() {
        assertArrayEquals(new byte[]{1, 2, 3},
                (byte[]) CborDecoder.decode(new byte[]{0x43, 1, 2, 3}));
        assertEquals("none", CborDecoder.decode(new byte[]{0x64, 'n', 'o', 'n', 'e'}));
    }

    @Test
    void decodesCoseKeyMap() {
        // {1: 2, 3: -7, -1: 1, -2: h'01', -3: h'02'}
        byte[] encoded = {
                (byte) 0xA5,
                0x01, 0x02,
                0x03, 0x26,
                0x20, 0x01,
                0x21, 0x41, 0x01,
                0x22, 0x41, 0x02};
        @SuppressWarnings("unchecked")
        Map<Object, Object> map = (Map<Object, Object>) CborDecoder.decode(encoded);
        assertEquals(2, ((Number) map.get(1L)).intValue());
        assertEquals(-7, ((Number) map.get(3L)).intValue());
        assertArrayEquals(new byte[]{1}, (byte[]) map.get(-2L));
    }

    @Test
    void decodesArraysAndBooleans() {
        byte[] array = {(byte) 0x83, 0x01, (byte) 0xF5, (byte) 0xF4};
        assertEquals(List.of(1L, true, false), CborDecoder.decode(array));
    }

    @Test
    void reportsConsumedLength() {
        byte[] data = {0x41, 0x01, 0x02};
        CborDecoder.Decoded decoded = CborDecoder.decodeFirst(data);
        assertEquals(2, decoded.consumed());
        assertArrayEquals(new byte[]{1}, (byte[]) decoded.value());
    }

    @Test
    void rejectsTruncatedOrTrailingData() {
        assertThrows(WebAuthnVerificationException.class,
                () -> CborDecoder.decode(new byte[]{0x43, 0x01}));
        assertThrows(WebAuthnVerificationException.class,
                () -> CborDecoder.decode(new byte[]{0x01, 0x02}));
        assertTrue(assertThrows(WebAuthnVerificationException.class,
                () -> CborDecoder.decode(new byte[]{0x1F})).getMessage().contains("Indefinite"));
    }
}